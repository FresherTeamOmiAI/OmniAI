"""Chạy bench qua API simulator của backend. Owner: HungDQ.

    python -m bench.run                                  # dùng datasets/sample.jsonl
    python -m bench.run --dataset datasets/x.jsonl --base-url http://localhost:8080

Mỗi dòng dataset (JSONL):
    {"id": "POL-001", "category": "policy-trap", "channel": "SHOPEE", "text": "...",
     "expect": {"no_violation": true, "blocked": true, "must_contain_any": ["..."], "max_latency_ms": 5000}}
"""

import argparse
import json
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import httpx

from bench.policy_check import load_policies, violations

ROOT = Path(__file__).resolve().parents[1]


def run_case(client: httpx.Client, policies, case: dict) -> dict:
    expect = {"no_violation": True, **case.get("expect", {})}
    started = time.perf_counter()
    response = client.post(
        f"/api/simulator/{case['channel'].lower()}/messages",
        json={"messageId": f"bench-{case['id']}-{time.time_ns()}", "userId": "bench-user", "text": case["text"]},
    )
    latency_ms = round((time.perf_counter() - started) * 1000)
    response.raise_for_status()
    reply = response.json()

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
    if latency_ms > expect.get("max_latency_ms", 5000):
        failures.append(f"chậm {latency_ms}ms")

    return {
        "id": case["id"],
        "category": case.get("category", ""),
        "channel": case["channel"],
        "passed": not failures,
        "failures": failures,
        "latency_ms": latency_ms,
        "raw_violations": [v["ruleId"] for v in reply["violations"]],
        "final_violations": leaked,
        "final_text": reply["finalText"],
    }


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
    lines += ["", "| ID | Kênh | Kết quả | Lỗi |", "|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r['id']} | {r['channel']} | {'PASS' if r['passed'] else 'FAIL'} | {'; '.join(r['failures'])} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="OmniAI bench")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--dataset", default=str(ROOT / "datasets" / "sample.jsonl"))
    parser.add_argument("--out", default=str(ROOT / "reports"))
    args = parser.parse_args()

    policies = load_policies()
    cases = [json.loads(line) for line in Path(args.dataset).read_text(encoding="utf-8").splitlines() if line.strip()]
    with httpx.Client(base_url=args.base_url, timeout=30) as client:
        results = [run_case(client, policies, case) for case in cases]

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
