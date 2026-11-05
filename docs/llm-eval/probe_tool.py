"""Một lần gọi tool thật cho cổng G6. Không ghi key ra file hay ra màn hình.

Đọc catalog local, gửi schema get_price_and_stock lên Groq, rồi lưu
request/response đã xóa Authorization vào results/tool-call.json.
"""

from __future__ import annotations

import csv
import json
import os
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CATALOG = ROOT / "shared" / "catalog" / "products.csv"
OUT = HERE / "results" / "tool-call.json"
MODEL = "openai/gpt-oss-20b"
URL = "https://api.groq.com/openai/v1/chat/completions"

TOOL = {
    "type": "function",
    "function": {
        "name": "get_price_and_stock",
        "description": "Tra giá và tồn kho một SKU trong catalog. Không bịa số.",
        "parameters": {
            "type": "object",
            "properties": {"sku": {"type": "string", "description": "Mã SKU, ví dụ LUMI-SUN-01"}},
            "required": ["sku"],
        },
    },
}


def load_dotenv() -> None:
    env_file = ROOT / ".env"
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() and key.strip() not in os.environ:
            os.environ[key.strip()] = value.strip()


def lookup(sku: str) -> dict:
    with CATALOG.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["sku"] == sku:
                return {
                    "sku": row["sku"],
                    "name": row["name"],
                    "price_web": int(row["price_web"]),
                    "stock": int(row["stock"]),
                }
    return {"error": f"không có SKU {sku}"}


def post(payload: dict, key: str) -> dict:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        URL,
        data=data,
        headers={
            "User-Agent": "omniai-llm-eval/1.0",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    load_dotenv()
    key = os.environ.get("GROQ_API_KEY", "")
    if not key:
        raise SystemExit("Thiếu GROQ_API_KEY.")
    first = {
        "model": MODEL,
        "temperature": 0.2,
        "max_tokens": 400,
        "reasoning_effort": "low",
        "tools": [TOOL],
        "tool_choice": "required",
        "messages": [
            {
                "role": "system",
                "content": "Khi khách hỏi giá hoặc tồn, gọi get_price_and_stock. Không tự nêu số trước khi có kết quả tool.",
            },
            {"role": "user", "content": "Kem chống nắng Lumi Lab mã LUMI-SUN-01 giá bao nhiêu và còn hàng không?"},
        ],
    }
    first_body = post(first, key)
    message = first_body["choices"][0]["message"]
    calls = message.get("tool_calls") or []
    if not calls:
        raise SystemExit("Model không gọi tool.")
    call = calls[0]
    args = json.loads(call["function"]["arguments"] or "{}")
    tool_result = lookup(str(args.get("sku", "")))
    second = {
        "model": MODEL,
        "temperature": 0.2,
        "max_tokens": 400,
        "reasoning_effort": "low",
        "tools": [TOOL],
        "messages": [
            *first["messages"],
            message,
            {
                "role": "tool",
                "tool_call_id": call["id"],
                "content": json.dumps(tool_result, ensure_ascii=False),
            },
        ],
    }
    second_body = post(second, key)
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(
        json.dumps(
            {
                "model": MODEL,
                "tool": "get_price_and_stock",
                "request_1": {k: v for k, v in first.items()},
                "response_1": first_body,
                "tool_result": tool_result,
                "request_2": {k: v for k, v in second.items() if k != "messages"},
                "response_2": second_body,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    final = second_body["choices"][0]["message"].get("content") or ""
    print(f"tool={call['function']['name']} sku={tool_result.get('sku')} wrote {OUT}")
    print(final[:300])


if __name__ == "__main__":
    main()
