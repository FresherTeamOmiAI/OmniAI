"""Đo phần đọc DB của ai-brain cho mỗi câu chat, không gọi LLM (US-43, T-43.2/T-43.4). Owner: LucVT.

Gọi đúng 2 hàm /chat đang dùng (app.catalog_access.load_public_catalog, app.rag.search.load_search_rows), đếm số
lần gọi run_select và số dòng đọc về. Trước f38632f mỗi run_select mở 1 kết nối psycopg mới; từ f38632f dùng lại
1 kết nối và cache kết quả 120 s, nên các lượt sau lượt đầu thường đọc từ cache.

    cd bench
    ..\\ai-brain\\.venv\\Scripts\\python -m perf.brain_db             # dùng CHATBOT_DB_URL trong môi trường
    ..\\ai-brain\\.venv\\Scripts\\python -m perf.brain_db --rounds 20 --json reports/brain-db-truoc.json

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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rounds", type=int, default=10, help="số lượt mô phỏng câu chat")
    parser.add_argument("--json", help="ghi kết quả ra file JSON")
    args = parser.parse_args(argv)

    if not os.getenv("CHATBOT_DB_URL", "").strip():
        print("Thiếu CHATBOT_DB_URL", file=sys.stderr)
        return 2
    sys.path.insert(0, str(AI_BRAIN))
    from app import catalog_access  # noqa: PLC0415 (cần sys.path của ai-brain)
    from app.rag import search  # noqa: PLC0415

    from perf.load_test import percentile  # noqa: PLC0415

    calls = {"n": 0, "rows": 0}
    original = catalog_access.run_select

    def counting(sql: str) -> list[tuple]:
        calls["n"] += 1
        rows = original(sql)
        calls["rows"] += len(rows)
        return rows

    catalog_access.run_select = counting
    search.run_select = counting

    totals, catalog_ms, search_ms = [], [], []
    connections = rows_read = 0
    search_rows = catalog_lines = 0
    for _ in range(args.rounds):
        calls.update(n=0, rows=0)
        t0 = time.perf_counter()
        snapshot = catalog_access.load_public_catalog("WEB", True)
        t1 = time.perf_counter()
        loaded = search.load_search_rows()
        t2 = time.perf_counter()
        catalog_ms.append((t1 - t0) * 1000)
        search_ms.append((t2 - t1) * 1000)
        totals.append((t2 - t0) * 1000)
        connections, rows_read = calls["n"], calls["rows"]
        catalog_lines, search_rows = len(snapshot.lines), len(loaded or [])

    result = {
        "rounds": args.rounds,
        "run_select_calls_per_chat": connections,
        "rows_read_per_chat": rows_read,
        "catalog_lines_in_prompt": catalog_lines,
        "search_rows": search_rows,
        "catalog_p50_ms": round(percentile(catalog_ms, 50), 1),
        "search_p50_ms": round(percentile(search_ms, 50), 1),
        "db_p50_ms": round(percentile(totals, 50), 1),
        "db_p95_ms": round(percentile(totals, 95), 1),
    }
    host = os.environ["CHATBOT_DB_URL"].split("@")[-1].split("/")[0]
    print(f"CHATBOT_DB_URL host: {host} · {args.rounds} lượt")
    for key, value in result.items():
        print(f"- {key}: {value}")
    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump({"db_host": host, **result}, fh, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
