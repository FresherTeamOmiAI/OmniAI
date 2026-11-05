"""Chạy 20 câu trên các model miễn phí. Task T-05.1.

Mọi model dùng system_prompt_for() và cùng temperature / max tokens.
Câu SHOPEE dùng prompt kênh sàn. Kết quả: results/<model>.jsonl

Chưa gọi API nếu thiếu key. Không ghi key vào repo.

Ví dụ (PowerShell), chỉ free tier, không gắn thẻ thanh toán:

  $env:GEMINI_API_KEY = "<key AI Studio>"
  $env:GROQ_API_KEY = "<key Groq>"
  $env:LLM_EVAL_MODELS = "gemini:gemini-3.8-flash,gemini:gemini-3.6-flash,groq:<model-id-free>"
  ai-brain\\.venv\\Scripts\\python docs\\llm-eval\\run_eval.py
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-brain"))

from app.agent.graph import system_prompt_for  # noqa: E402
from app.schemas import ChatRequest  # noqa: E402

TEMPERATURE = 0.2
MAX_TOKENS = 400
PAUSE_SECONDS = 15
HERE = Path(__file__).resolve().parent
QUESTIONS = HERE / "questions.jsonl"
RESULTS = HERE / "results"


def load_dotenv() -> None:
    """Đọc key từ .env nếu biến môi trường chưa có. Không ghi đè biến đã set."""
    env_file = ROOT / ".env"
    if not env_file.exists():
        return
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key and key not in os.environ:
            os.environ[key] = value.strip()


def load_questions() -> list[dict]:
    rows = []
    for line in QUESTIONS.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    if len(rows) != 20:
        raise SystemExit(f"Cần đúng 20 câu, đang có {len(rows)}")
    return rows


def prompt_for(row: dict) -> str:
    request = ChatRequest(
        message_id=row["id"],
        channel=row["channel"],
        channel_owned=row["channel_owned"],
        user_id="eval",
        text=row["text"],
    )
    return system_prompt_for(request)


def post_json(url: str, payload: dict, headers: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    headers = {"User-Agent": "omniai-llm-eval/1.0", **headers}
    request = urllib.request.Request(url, data=data, headers=headers, method="POST")
    for attempt in range(6):
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = json.loads(response.read().decode("utf-8"))
            elapsed_ms = round((time.perf_counter() - started) * 1000)
            return {"body": body, "latency_ms": elapsed_ms}
        except (TimeoutError, urllib.error.URLError):
            if attempt < 5:
                time.sleep(8)
                continue
            return {"error": "HTTP timeout", "latency_ms": 60000}
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            if error.code in (429, 503) and attempt < 5:
                match = re.search(r"try again in ([0-9.]+)", detail)
                time.sleep(float(match.group(1)) + 0.5 if match else 8)
                continue
            elapsed_ms = round((time.perf_counter() - started) * 1000)
            return {"error": f"HTTP {error.code}: {detail[:500]}", "latency_ms": elapsed_ms}
    return {"error": "HTTP 429: hết số lần thử lại", "latency_ms": 0}


def call_gemini(model: str, system: str, text: str) -> tuple[str, int]:
    key = os.environ.get("GEMINI_API_KEY") or os.environ.get("LLM_API_KEY")
    if not key:
        raise SystemExit("Thiếu GEMINI_API_KEY (free tier AI Studio, không gắn thẻ).")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    payload = {
        "systemInstruction": {"parts": [{"text": system}]},
        "contents": [{"role": "user", "parts": [{"text": text}]}],
        "generationConfig": {
            "temperature": TEMPERATURE,
            "maxOutputTokens": MAX_TOKENS,
            "thinkingConfig": {"thinkingLevel": "low"},
        },
    }
    result = post_json(url, payload, {"Content-Type": "application/json", "x-goog-api-key": key})
    if "error" in result:
        return result["error"], result["latency_ms"]
    parts = result["body"]["candidates"][0]["content"].get("parts", [])
    answer = "".join(part.get("text", "") for part in parts if not part.get("thought"))
    return answer.strip(), result["latency_ms"]


def call_openai_compatible(provider: str, model: str, system: str, text: str) -> tuple[str, int]:
    if provider == "groq":
        key = os.environ.get("GROQ_API_KEY")
        url = "https://api.groq.com/openai/v1/chat/completions"
        if not key:
            raise SystemExit("Thiếu GROQ_API_KEY.")
    elif provider == "openrouter":
        key = os.environ.get("OPENROUTER_API_KEY")
        url = "https://openrouter.ai/api/v1/chat/completions"
        if not key:
            raise SystemExit("Thiếu OPENROUTER_API_KEY.")
    else:
        raise SystemExit(f"Provider '{provider}' không nằm trong free tier của script này.")
    payload = {
        "model": model,
        "temperature": TEMPERATURE,
        "max_tokens": MAX_TOKENS,
        "tool_choice": "none",
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": text},
        ],
    }
    # gpt-oss mặc định nghĩ dài và hết 400 token trước khi viết câu trả lời.
    # low giữ cùng max_tokens nhưng câu khách đọc được nằm ở content.
    if "gpt-oss" in model:
        payload["reasoning_effort"] = "low"
    result = post_json(
        url,
        payload,
        {"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
    )
    if "error" in result:
        return result["error"], result["latency_ms"]
    message = result["body"]["choices"][0]["message"]
    answer = message.get("content") or ""
    return answer.strip(), result["latency_ms"]


def call_model(spec: str, system: str, text: str) -> tuple[str, int]:
    provider, model = spec.split(":", 1)
    if provider == "gemini":
        return call_gemini(model, system, text)
    return call_openai_compatible(provider, model, system, text)


def main() -> None:
    load_dotenv()
    specs = [item.strip() for item in os.environ.get("LLM_EVAL_MODELS", "").split(",") if item.strip()]
    if not specs:
        raise SystemExit(
            "Đặt LLM_EVAL_MODELS, ví dụ gemini:gemini-3.8-flash. "
            "T-05.1 cần ít nhất 3 model; chạy 1 model trước để kiểm tra key."
        )
    if len(specs) < 3:
        print("Cảnh báo: dưới 3 model. Đủ để thử key, chưa đủ nộp T-05.1.")
    questions = load_questions()
    RESULTS.mkdir(exist_ok=True)
    for spec in specs:
        safe_name = spec.split(":", 1)[1].replace("/", "_").replace(":", "_")
        out = RESULTS / f"{safe_name}.jsonl"
        lines = []
        for row in questions:
            answer, latency_ms = call_model(spec, prompt_for(row), row["text"])
            lines.append(
                json.dumps(
                    {
                        "id": row["id"],
                        "answer": answer,
                        "latency_ms": latency_ms,
                        "model": spec,
                        "temperature": TEMPERATURE,
                        "max_tokens": MAX_TOKENS,
                    },
                    ensure_ascii=False,
                )
            )
            print(f"{spec} {row['id']} {latency_ms} ms", flush=True)
            out.write_text("\n".join(lines) + "\n", encoding="utf-8")
            time.sleep(PAUSE_SECONDS)
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
