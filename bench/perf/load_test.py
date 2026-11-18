"""Load test API catalog và hội thoại (US-43, T-43.2). Owner: LucVT.

Đo trước và sau khi tối ưu (T-43.1, T-43.3, T-43.4), ghi kết quả vào docs/tracking/perf-US-43.md.

    cd bench
    python -m perf.load_test                          # backend :8080, 200 request, 10 luồng mỗi kịch bản
    python -m perf.load_test --requests 500 --workers 20 --json reports/perf-truoc.json

Kịch bản:
- store_list: GET /api/products?page=0..N&size=20 (đúng request của trang store).
- product_detail: GET /api/products/{id} với id ngẫu nhiên trong các SKU đang bán.
- conversations: GET /api/conversations?size=20 bằng Basic admin (seed 10.000 tin bằng seed_conversations.sql trước).
- widget_catalog: lặp GET /api/products?page=N&size=100 tới trang cuối như ChatWidget đang làm, đo cả vòng (tuần tự).
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import statistics
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict, dataclass

import httpx

STORE_PAGE_SIZE = 20
WIDGET_PAGE_SIZE = 100


def percentile(values: list[float], p: float) -> float:
    """Phân vị theo nearest rank: phần tử thứ ceil(p/100 * n) của dãy đã sắp xếp (1-based)."""
    if not values:
        raise ValueError("không có số liệu")
    if not 0 < p <= 100:
        raise ValueError("p phải trong (0, 100]")
    ordered = sorted(values)
    return ordered[math.ceil(p / 100 * len(ordered)) - 1]


@dataclass
class Result:
    scenario: str
    requests: int
    errors: int
    p50_ms: float
    p95_ms: float
    max_ms: float
    mean_kb: float


def summarize(scenario: str, samples: list[tuple[float, int, bool]]) -> Result:
    """samples: (thời gian ms, số byte body, thành công)."""
    ok = [s for s in samples if s[2]]
    times = [s[0] for s in ok] or [0.0]
    return Result(
        scenario=scenario,
        requests=len(samples),
        errors=len(samples) - len(ok),
        p50_ms=round(percentile(times, 50), 1),
        p95_ms=round(percentile(times, 95), 1),
        max_ms=round(max(times), 1),
        mean_kb=round(statistics.fmean(s[1] for s in ok) / 1024, 1) if ok else 0.0,
    )


def timed_get(client: httpx.Client, url: str, **kwargs) -> tuple[float, int, bool]:
    start = time.perf_counter()
    try:
        response = client.get(url, **kwargs)
        body = response.content
        return (time.perf_counter() - start) * 1000, len(body), response.status_code == 200
    except httpx.HTTPError:
        return (time.perf_counter() - start) * 1000, 0, False


def run_parallel(client: httpx.Client, urls: list[str], workers: int, **kwargs) -> list[tuple[float, int, bool]]:
    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(lambda u: timed_get(client, u, **kwargs), urls))


def widget_round(client: httpx.Client) -> tuple[float, int, bool, int]:
    """Một lần tải catalog của widget: lặp trang 100 SP tới trang cuối. Trả (ms, byte, thành công, số request)."""
    start = time.perf_counter()
    total_bytes, page, calls, loaded = 0, 0, 0, 0
    while True:
        response = client.get(f"/api/products?page={page}&size={WIDGET_PAGE_SIZE}")
        calls += 1
        if response.status_code != 200:
            return (time.perf_counter() - start) * 1000, total_bytes, False, calls
        total_bytes += len(response.content)
        data = response.json()
        loaded += len(data["items"])
        # Cùng điều kiện dừng với ChatWidget.tsx: đủ total hoặc trang rỗng.
        if loaded >= data["total"] or not data["items"]:
            return (time.perf_counter() - start) * 1000, total_bytes, True, calls
        page += 1


def active_product_ids(client: httpx.Client) -> tuple[list[int], int]:
    """Id các SKU đang bán và số trang store, đọc qua chính API store."""
    ids: list[int] = []
    page, total_pages = 0, 1
    while page < total_pages:
        data = client.get(f"/api/products?page={page}&size={STORE_PAGE_SIZE}").raise_for_status().json()
        ids.extend(item["id"] for item in data["items"])
        total_pages = math.ceil(data["total"] / STORE_PAGE_SIZE)
        page += 1
    return ids, total_pages


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=os.getenv("BACKEND_URL", "http://localhost:8080"))
    parser.add_argument("--requests", type=int, default=200, help="số request mỗi kịch bản song song")
    parser.add_argument("--workers", type=int, default=10)
    parser.add_argument("--widget-rounds", type=int, default=20, help="số lần lặp vòng tải catalog của widget")
    parser.add_argument("--admin-user", default=os.getenv("ADMIN_USERNAME", "admin"))
    parser.add_argument("--admin-password", default=os.getenv("ADMIN_PASSWORD", "admin"))
    parser.add_argument("--json", help="ghi kết quả ra file JSON")
    parser.add_argument("--seed", type=int, default=43)
    args = parser.parse_args(argv)

    rng = random.Random(args.seed)
    limits = httpx.Limits(max_connections=args.workers, max_keepalive_connections=args.workers)
    with httpx.Client(base_url=args.base_url, timeout=30, limits=limits) as client:
        ids, pages = active_product_ids(client)
        # Làm nóng JVM/JIT và pool kết nối để số đo không gồm lần chạy đầu.
        for page in range(min(pages, 5)):
            client.get(f"/api/products?page={page}&size={STORE_PAGE_SIZE}")

        results = [
            summarize("store_list", run_parallel(
                client, [f"/api/products?page={i % pages}&size={STORE_PAGE_SIZE}" for i in range(args.requests)],
                args.workers)),
            summarize("product_detail", run_parallel(
                client, [f"/api/products/{rng.choice(ids)}" for _ in range(args.requests)], args.workers)),
            summarize("conversations", run_parallel(
                client, ["/api/conversations?size=20"] * args.requests, args.workers,
                auth=(args.admin_user, args.admin_password))),
        ]
        rounds = [widget_round(client) for _ in range(args.widget_rounds)]
        results.append(summarize("widget_catalog", [r[:3] for r in rounds]))
        widget_calls = rounds[0][3] if rounds else 0

    print(f"Backend {args.base_url} · {len(ids)} SKU đang bán · {pages} trang store · "
          f"{args.requests} request/kịch bản, {args.workers} luồng · widget {widget_calls} request/vòng")
    print("| Kịch bản | Request | Lỗi | p50 (ms) | p95 (ms) | max (ms) | KB/response |")
    print("|---|---|---|---|---|---|---|")
    for r in results:
        print(f"| {r.scenario} | {r.requests} | {r.errors} | {r.p50_ms} | {r.p95_ms} | {r.max_ms} | {r.mean_kb} |")

    if args.json:
        payload = {"base_url": args.base_url, "active_skus": len(ids), "store_pages": pages,
                   "workers": args.workers, "widget_requests_per_round": widget_calls,
                   "results": [asdict(r) for r in results]}
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=2)
    return 1 if any(r.errors for r in results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
