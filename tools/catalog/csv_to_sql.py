"""Sinh migration Flyway từ shared/catalog/products.csv (T-08.2). Owner: LucVT.

    python tools/catalog/csv_to_sql.py V5__import_catalog_80.sql
    python tools/catalog/csv_to_sql.py V9__catalog_update.sql path/to/x.csv

Ghi vào backend/src/main/resources/db/migration/<tên file>. Chạy validate_catalog.py trước, có lỗi thì dừng.
Mỗi dòng là upsert theo `sku` (ON CONFLICT DO UPDATE), nên SKU đã có (vd. 8 SKU của V2) được cập nhật,
SKU mới được thêm, không xoá SKU nào. Muốn đổi catalog sau khi migration đã merge thì sửa CSV rồi sinh
file V<n> MỚI, không sửa file cũ (Flyway kiểm checksum).
Chỉ dùng thư viện chuẩn.
"""

import csv
import sys
from pathlib import Path

from validate_catalog import DEFAULT_CSV, HEADER, ROOT, validate

MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
INT_COLUMNS = {"size_ml", "pao_months", "price_web", "price_shopee", "price_tiktok", "stock"}
NULLABLE = {"skin_concerns", "usage_time", "description"}


def sql_value(column: str, raw: str) -> str:
    if column in INT_COLUMNS:
        return str(int(raw))
    if column == "active":
        return "TRUE" if raw == "true" else "FALSE"
    if column in NULLABLE and not raw.strip():
        return "NULL"
    return "'" + raw.replace("'", "''") + "'"


def render(rows: list[dict[str, str]], source: str) -> str:
    updates = ",\n    ".join(f"{c} = EXCLUDED.{c}" for c in HEADER if c != "sku")
    values = ",\n".join("(" + ", ".join(sql_value(c, row[c]) for c in HEADER) + ")" for row in rows)
    return (
        f"-- Catalog {len(rows)} SKU từ {source} (T-08.2, US-08). Owner: LucVT.\n"
        "-- FILE SINH TỰ ĐỘNG bởi tools/catalog/csv_to_sql.py, không sửa tay.\n"
        "-- Upsert theo sku: SKU đã có thì cập nhật, SKU mới thì thêm, không xoá SKU nào.\n\n"
        f"INSERT INTO product ({', '.join(HEADER)}) VALUES\n"
        f"{values}\n"
        "ON CONFLICT (sku) DO UPDATE SET\n"
        f"    {updates},\n"
        "    updated_at = now();\n"
    )


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    target = MIGRATION_DIR / sys.argv[1]
    source = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_CSV
    if target.exists():
        print(f"LỖI {target.name} đã tồn tại. Migration đã có thì không ghi đè, hãy đặt V<n> mới.")
        return 1

    errors = validate(source)
    for e in errors:
        print("LỖI", e)
    if errors:
        return 1

    with source.open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    try:
        rel = source.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        rel = source.name
    target.write_text(render(rows, rel), encoding="utf-8", newline="\n")
    print(f"OK: {len(rows)} SKU -> {target.relative_to(ROOT).as_posix()}")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
