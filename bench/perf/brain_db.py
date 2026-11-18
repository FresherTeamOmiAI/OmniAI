"""Đo phần đọc DB và xếp hạng của ai-brain cho mỗi câu chat, không gọi LLM (US-43, T-43.2/T-43.4). Owner: LucVT.

Gọi đúng các hàm /chat dùng trước khi tới LLM: app.rag.search.load_search_rows (catalog nhớ trong bộ nhớ, T-43.4),
search_product (gợi ý hoặc top-N cho prompt) và catalog_snapshot (dòng + giá đưa cho LLM). Đếm số câu SELECT,
số dòng đọc về, thời gian từng chặng ở 2 trạng thái:
- lạnh: cache vừa hết hạn (xoá cache trước mỗi lượt), tức câu chat đầu tiên sau mỗi CATALOG_CACHE_TTL_SECONDS;
- ấm: các câu chat trong thời gian TTL.

    cd bench
    ..\\ai-brain\\.venv\\Scripts\\python -m perf.brain_db             # dùng CHATBOT_DB_URL trong môi trường
    ..\\ai-brain\\.venv\\Scripts\\python -m perf.brain_db --rounds 20 --json reports/brain-db-sau.json

Chỉ SELECT bằng role chatbot_reader (read-only), không ghi gì vào DB.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

AI_BRAIN = Path(__file__).resolve().parents[2] / "ai-brain"
QUESTION = "kem chống nắng cho da dầu dưới 300k"
OPEN_QUESTION = "da mình hay đổ dầu buổi trưa thì dùng gì"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rounds", type=int, default=10, help="số lượt mô phỏng câu chat cho mỗi trạng thái")
    parser.add_argument("--json", help="ghi kết quả ra file JSON")
    args = parser.parse_args(argv)

    if not os.getenv("CHATBOT_DB_URL", "").strip():
        print("Thiếu CHATBOT_DB_URL", file=sys.stderr)
        return 2
    sys.path.insert(0, str(AI_BRAIN))
    from app.agent.graph import PROMPT_PRODUCTS  # noqa: PLC0415 (cần sys.path của ai-brain)
    from app.rag import search  # noqa: PLC0415

    from perf.load_test import percentile  # noqa: PLC0415

    calls = {"n": 0, "rows": 0}
    original = search.run_select

    def counting(sql: str) -> list[tuple]:
        calls["n"] += 1
        rows = original(sql)
        calls["rows"] += len(rows)
        return rows

    search.run_select = counting

    def one_chat(question: str) -> dict:
        calls.update(n=0, rows=0)
        t0 = time.perf_counter()
        rows = search.load_search_rows() or []
        t1 = time.perf_counter()
        # Giống graph.respond: có ràng buộc thì top 3 gợi ý, không thì top-N gần câu hỏi cho prompt.
        hits = search.search_product(question, rows, limit=3) if search.parse_constraints(question).active else []
        shown = hits or search.search_product(question, rows, limit=PROMPT_PRODUCTS)
        snapshot = search.catalog_snapshot(shown, "WEB", True)
        t2 = time.perf_counter()
        return {
            "selects": calls["n"],
            "rows_read": calls["rows"],
            "catalog_rows": len(rows),
            "prompt_lines": len(snapshot.lines),
            "db_ms": (t1 - t0) * 1000,
            "rank_ms": (t2 - t1) * 1000,
        }

    result: dict = {"rounds": args.rounds}
    for state in ("cold", "warm"):
        samples = []
        search.clear_cache()
        if state == "warm":
            one_chat(QUESTION)  # nạp cache 1 lần, các lượt đo sau nằm trong TTL
        for index in range(args.rounds):
            if state == "cold":
                search.clear_cache()
            samples.append(one_chat(QUESTION if index % 2 == 0 else OPEN_QUESTION))
        last = samples[-1]
        if last["catalog_rows"] == 0:
            print("Không đọc được catalog bằng CHATBOT_DB_URL (xem cảnh báo ở trên)", file=sys.stderr)
            return 1
        db = [s["db_ms"] for s in samples]
        rank = [s["rank_ms"] for s in samples]
        result[state] = {
            "selects_per_chat": last["selects"],
            "rows_read_per_chat": last["rows_read"],
            "catalog_rows": last["catalog_rows"],
            "prompt_lines": max(s["prompt_lines"] for s in samples),
            "db_p50_ms": round(percentile(db, 50), 1),
            "db_p95_ms": round(percentile(db, 95), 1),
            "rank_p50_ms": round(percentile(rank, 50), 1),
        }
    host = os.environ["CHATBOT_DB_URL"].split("@")[-1].split("/")[0]
    print(f"CHATBOT_DB_URL host: {host} · {args.rounds} lượt mỗi trạng thái")
    for state in ("cold", "warm"):
        print(f"[{state}]")
        for key, value in result[state].items():
            print(f"- {key}: {value}")
    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump({"db_host": host, **result}, fh, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
