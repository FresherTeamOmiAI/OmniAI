"""Chạy 10 câu mẫu tiếng Việt trên provider đang chọn. Task T-06.4.

Không ghi key. Bỏ qua nếu thiếu LLM_API_KEY (pytest không gọi file này).

    cd ai-brain
    ..\\.venv không dùng. Từ thư mục ai-brain:
    .\\.venv\\Scripts\\python samples\\run_vi_context.py
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

SAMPLES = Path(__file__).with_name("vi-context.jsonl")
RESULTS = Path(__file__).with_name("vi-context-results.jsonl")


def load_dotenv() -> None:
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
    if not os.environ.get("LLM_API_KEY") and os.environ.get("GEMINI_API_KEY"):
        os.environ["LLM_API_KEY"] = os.environ["GEMINI_API_KEY"]
    os.environ.setdefault("LLM_PROVIDER", "gemini")
    os.environ.setdefault("LLM_MODEL", "gemini-3.6-flash")


def retry_delay_seconds(message: str) -> float:
    """Đọc 'retry in 4h21m52s' hoặc 'retry in 12.5s'. Không cắt mất phần giờ."""
    match = re.search(
        r"retry in (?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?",
        message,
    )
    if not match or not any(match.groups()):
        return 20
    hours = int(match.group(1) or 0)
    minutes = int(match.group(2) or 0)
    seconds = float(match.group(3) or 0)
    return hours * 3600 + minutes * 60 + seconds + 1


def load_passed() -> dict[str, dict]:
    if not RESULTS.exists():
        return {}
    passed = {}
    for line in RESULTS.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        if item.get("pass"):
            passed[item["id"]] = item
    return passed


def passes(row: dict, answer: str) -> tuple[bool, str]:
    lowered = answer.lower()
    if len(answer.strip()) < 40:
        return False, "quá ngắn"
    if not any(token in lowered for token in ("dạ", "bạn", "da", "shop", "ạ", "không", "nên")):
        return False, "không thấy tiếng Việt"
    missing = [word for word in row["must_contain_any"] if word.lower() not in lowered]
    if missing:
        return False, "thiếu " + ", ".join(missing)
    forbidden = [word for word in row["must_not_contain"] if word.lower() in lowered]
    if forbidden:
        return False, "có " + ", ".join(forbidden)
    return True, ""


def main() -> None:
    load_dotenv()
    if not os.environ.get("LLM_API_KEY"):
        raise SystemExit("Cần LLM_API_KEY trong .env. Không gọi API khi thiếu key.")

    from app.agent.graph import respond
    from app.llm.factory import get_provider
    from app.schemas import ChatRequest

    provider = get_provider()
    rows = [json.loads(line) for line in SAMPLES.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(rows) != 10:
        raise SystemExit(f"Cần đúng 10 câu mẫu, đang có {len(rows)}")
    passed = load_passed()
    written = [passed[row["id"]] for row in rows if row["id"] in passed]
    failed = 0
    long_wait_used = False
    for row in rows:
        if row["id"] in passed:
            print(f"{row['id']} PASS kept", flush=True)
            continue
        request = ChatRequest(
            message_id=row["id"],
            channel=row["channel"],
            channel_owned=row["channel_owned"],
            user_id="sample",
            text=row["text"],
        )
        reply = None
        latency_ms = 0
        for attempt in range(6):
            started = time.perf_counter()
            try:
                reply = respond(request, provider)
                latency_ms = round((time.perf_counter() - started) * 1000)
                break
            except RuntimeError as error:
                message = str(error)
                if "HTTP 429" not in message and "HTTP 503" not in message:
                    raise
                if attempt == 5:
                    raise
                delay = retry_delay_seconds(message)
                if delay > 120:
                    if long_wait_used:
                        raise
                    long_wait_used = True
                print(f"{row['id']} retry {attempt + 1} after {delay:.0f}s", flush=True)
                time.sleep(delay)
        ok, reason = passes(row, reply.reply)
        finish = getattr(provider, "last_finish_reason", "")
        if finish == "MAX_TOKENS":
            ok, reason = False, "bị cắt vì MAX_TOKENS"
        if not ok:
            failed += 1
        usage = getattr(provider, "last_usage", {}) or {}
        item = {
            "id": row["id"],
            "pass": ok,
            "reason": reason,
            "latency_ms": latency_ms,
            "finish_reason": finish,
            "thoughts_token_count": usage.get("thoughtsTokenCount"),
            "candidates_token_count": usage.get("candidatesTokenCount"),
            "reply": reply.reply,
            "warnings": reply.warnings,
            "provider": reply.provider,
            "model": getattr(provider, "model", provider.name),
        }
        written.append(item)
        RESULTS.write_text(
            "\n".join(json.dumps(saved, ensure_ascii=False) for saved in written) + "\n",
            encoding="utf-8",
        )
        print(f"{row['id']} {'PASS' if ok else 'FAIL'} {latency_ms} ms {reason}", flush=True)
        time.sleep(2)
    latencies = sorted(item["latency_ms"] for item in written)
    p95 = latencies[max(0, round(len(latencies) * 0.95) - 1)]
    print(f"passed {len(written) - failed}/10 p95_ms {p95} wrote {RESULTS}")
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
