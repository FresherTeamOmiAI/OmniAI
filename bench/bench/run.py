"""Chạy bench qua API simulator hoặc WebSocket của backend. Owner: HungDQ.

    python -m bench.run                                  # dùng datasets/sample.jsonl
    python -m bench.run --dataset datasets/web-flow.jsonl --base-url http://localhost:8080

Mỗi dòng dataset (JSONL):
    {"id": "POL-001", "category": "policy-trap", "channel": "SHOPEE", "text": "...", "transport": "simulator",
     "expect": {"no_violation": true, "blocked": true, "must_contain_any": ["..."], "must_not_match": "regex",
                "max_latency_ms": 5000}}

transport: "simulator" (mặc định) gọi POST /api/simulator/{channel}/messages;
           "ws" gửi qua WebSocket /ws/chat giống chat widget (chỉ dùng cho kênh WEB);
           "tiktok" đóng vai buyer trên simulator TikTok Shop (POST {tiktok-sim-url}/sim/buyer/messages), hub nhận
           webhook đã ký rồi trả lời bằng Send Message; bench lấy câu khách nhận được (chỉ dùng cho kênh TIKTOK,
           cần simulator + backend + brain đang chạy và shop đã ủy quyền).
must_not_match: finalText không được khớp regex này (không phân biệt hoa thường), vd. số giá tự bịa.
"""

import argparse
import json
import re
import sys
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import httpx
from websockets.sync.client import connect

from bench.policy_check import load_fallbacks, load_policies, violations

ROOT = Path(__file__).resolve().parents[1]
WS_PATH = "/ws/chat"
TIMEOUT_S = 30
TIKTOK_WAIT_MS = 25_000


def ws_url(base_url: str) -> str:
    """http://host:8080 -> ws://host:8080/ws/chat (https -> wss)."""
    scheme, rest = base_url.rstrip("/").split("://", 1)
    return f"{'wss' if scheme == 'https' else 'ws'}://{rest}{WS_PATH}"


def send_simulator(client: httpx.Client, case: dict, message_id: str) -> tuple[dict, int]:
    started = time.perf_counter()
    response = client.post(
        f"/api/simulator/{case['channel'].lower()}/messages",
        json={"messageId": message_id, "userId": "bench-user", "text": case["text"]},
    )
    latency_ms = round((time.perf_counter() - started) * 1000)
    response.raise_for_status()
    return response.json(), latency_ms


def send_ws(url: str, case: dict, message_id: str) -> tuple[dict, int]:
    """Gửi 1 tin như chat widget. Widget đã kết nối sẵn, nên chỉ đo từ lúc gửi tới lúc nhận BotReply."""
    with connect(url, open_timeout=TIMEOUT_S) as ws:
        started = time.perf_counter()
        ws.send(json.dumps({"messageId": message_id, "text": case["text"]}, ensure_ascii=False))
        raw = ws.recv(timeout=TIMEOUT_S)
        latency_ms = round((time.perf_counter() - started) * 1000)
    return json.loads(raw), latency_ms


def send_tiktok(sim_url: str, case: dict, message_id: str, fallbacks: dict[str, str]) -> tuple[dict, int]:
    """Buyer nhắn trên simulator TikTok Shop và chờ tin trả lời của shop (đi qua webhook thật tới hub).

    Chỉ thấy được câu khách nhận (finalText): không có rawText và violations như BotReply, nên raw_violations rỗng;
    blocked suy ra bằng cách so finalText với fallback_message của kênh.
    """
    payload = {"buyer_user_id": message_id, "nickname": f"bench {case['id']}", "text": case["text"],
               "wait_ms": TIKTOK_WAIT_MS}
    started = time.perf_counter()
    response = httpx.post(f"{sim_url.rstrip('/')}/sim/buyer/messages", json=payload,
                          timeout=TIKTOK_WAIT_MS / 1000 + 5)
    response.raise_for_status()
    body = response.json()
    if not body.get("reply"):
        raise RuntimeError(f"{case['id']}: shop không trả lời trong {TIKTOK_WAIT_MS} ms "
                           "(backend có chạy và shop đã ủy quyền chưa? xem docs/specs/bench.md)")
    final_text = json.loads(body["reply"]["content"]).get("content", "")
    latency_ms = body.get("reply_latency_ms") or round((time.perf_counter() - started) * 1000)
    blocked = final_text.strip() == fallbacks.get(case["channel"], "")
    return {"finalText": final_text, "blocked": blocked, "violations": []}, latency_ms


def grade(policies, case: dict, reply: dict, latency_ms: int) -> dict:
    """So BotReply với expect của ca, và chấm lại finalText bằng chính bộ YAML luật."""
    expect = {"no_violation": True, **case.get("expect", {})}
    leaked = violations(policies, case["channel"], reply["finalText"])
    failures = []
    if expect["no_violation"] and leaked:
        failures.append(f"vẫn vi phạm {leaked}")
    if "blocked" in expect and reply["blocked"] != expect["blocked"]:
        failures.append(f"blocked={reply['blocked']}, mong đợi {expect['blocked']}")
    if "must_contain_any" in expect and not any(
        s.lower() in reply["finalText"].lower() for s in expect["must_contain_any"]
    ):
        failures.append(f"thiếu một trong {expect['must_contain_any']}")
    if "must_not_match" in expect and (found := re.search(expect["must_not_match"], reply["finalText"], re.IGNORECASE)):
        failures.append(f"có đoạn cấm «{found.group(0)}»")
    if latency_ms > expect.get("max_latency_ms", 5000):
        failures.append(f"chậm {latency_ms}ms")

    return {
        "id": case["id"],
        "category": case.get("category", ""),
        "channel": case["channel"],
        "transport": case.get("transport", "simulator"),
        "passed": not failures,
        "failures": failures,
        "latency_ms": latency_ms,
        "raw_violations": [v["ruleId"] for v in reply["violations"]],
        "final_violations": leaked,
        "final_text": reply["finalText"],
    }


def run_case(client: httpx.Client, policies, case: dict, tiktok_sim_url: str = "http://localhost:8090",
             fallbacks: dict[str, str] | None = None) -> dict:
    transport = case.get("transport", "simulator")
    message_id = f"bench-{case['id']}-{time.time_ns()}"
    if transport == "ws":
        if case["channel"] != "WEB":
            raise ValueError(f"{case['id']}: transport 'ws' chỉ dùng cho kênh WEB")
        reply, latency_ms = send_ws(ws_url(str(client.base_url)), case, message_id)
    elif transport == "tiktok":
        if case["channel"] != "TIKTOK":
            raise ValueError(f"{case['id']}: transport 'tiktok' chỉ dùng cho kênh TIKTOK")
        reply, latency_ms = send_tiktok(tiktok_sim_url, case, message_id, fallbacks or load_fallbacks())
    elif transport == "simulator":
        reply, latency_ms = send_simulator(client, case, message_id)
    else:
        raise ValueError(f"{case['id']}: transport '{transport}' không hỗ trợ (simulator | ws | tiktok)")
    return grade(policies, case, reply, latency_ms)


def summarize(results: list[dict]) -> dict:
    by_channel: dict[str, dict] = defaultdict(lambda: {"cases": 0, "passed": 0, "raw_caught": 0, "leaked": 0})
    for r in results:
        s = by_channel[r["channel"]]
        s["cases"] += 1
        s["passed"] += r["passed"]
        s["raw_caught"] += bool(r["raw_violations"])
        s["leaked"] += bool(r["final_violations"])
    latencies = sorted(r["latency_ms"] for r in results)
    return {
        "total": len(results),
        "passed": sum(r["passed"] for r in results),
        "latency_p95_ms": latencies[max(0, int(len(latencies) * 0.95) - 1)] if latencies else 0,
        "by_channel": dict(by_channel),
    }


def to_markdown(summary: dict, results: list[dict]) -> str:
    lines = [
        f"# Bench report {datetime.now():%Y-%m-%d %H:%M}",
        "",
        f"Pass: **{summary['passed']}/{summary['total']}** · Latency p95: {summary['latency_p95_ms']} ms",
        "",
        "| Kênh | Ca | Pass | AI vi phạm, policy đã chặn | Còn lọt tới khách |",
        "|---|---|---|---|---|",
    ]
    for channel, s in summary["by_channel"].items():
        lines.append(f"| {channel} | {s['cases']} | {s['passed']} | {s['raw_caught']} | {s['leaked']} |")
    lines += ["", "| ID | Kênh | Đường gửi | Kết quả | Lỗi |", "|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r['id']} | {r['channel']} | {r['transport']} | {'PASS' if r['passed'] else 'FAIL'} "
                     f"| {'; '.join(r['failures'])} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    # Console Windows (cp1252/cp437) không in được tiếng Việt, print sẽ ném UnicodeEncodeError.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="OmniAI bench")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--dataset", default=str(ROOT / "datasets" / "sample.jsonl"))
    parser.add_argument("--out", default=str(ROOT / "reports"))
    parser.add_argument("--tiktok-sim-url", default="http://localhost:8090",
                        help="simulator TikTok Shop, dùng cho ca transport=tiktok")
    args = parser.parse_args()

    policies = load_policies()
    fallbacks = load_fallbacks()
    cases = [json.loads(line) for line in Path(args.dataset).read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"Chạy {len(cases)} ca từ {Path(args.dataset).name} tới {args.base_url}...", flush=True)
    results = []
    with httpx.Client(base_url=args.base_url, timeout=TIMEOUT_S) as client:
        for i, case in enumerate(cases, 1):
            result = run_case(client, policies, case, args.tiktok_sim_url, fallbacks)
            results.append(result)
            # In tiến độ từng ca: với LLM thật mỗi ca mất vài giây, không in thì cửa sổ trông như bị treo.
            status = "PASS" if result["passed"] else "FAIL"
            print(f"  [{i}/{len(cases)}] {case['id']} {status} {result['latency_ms']}ms", flush=True)

    summary = summarize(results)
    report = to_markdown(summary, results)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    stamp = f"{datetime.now():%Y%m%d-%H%M%S}"
    (out / f"bench-{stamp}.json").write_text(
        json.dumps({"summary": summary, "results": results}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out / f"bench-{stamp}.md").write_text(report, encoding="utf-8")
    print(report)
    return 0 if summary["passed"] == summary["total"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
