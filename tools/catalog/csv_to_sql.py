"""Sinh migration Flyway từ shared/catalog/products.csv (T-08.2). Owner: LucVT.

    python tools/catalog/csv_to_sql.py V5__import_catalog_80.sql
    python tools/catalog/csv_to_sql.py V10__import_catalog_real.sql            # catalog thật (T-47.1)
    python tools/catalog/csv_to_sql.py V14__catalog_update.sql path/to/x.csv   # sau V13: có product_channel

Ghi vào backend/src/main/resources/db/migration/<tên file>. Chạy validate_catalog.py trước, có lỗi thì dừng.
Mỗi dòng là upsert theo `sku` (ON CONFLICT DO UPDATE), nên SKU đã có (vd. 8 SKU của V2) được cập nhật,
SKU mới được thêm, không xoá SKU nào. Muốn đổi catalog sau khi migration đã merge thì sửa CSV rồi sinh
file V<n> MỚI, không sửa file cũ (Flyway kiểm checksum).
CSV có thêm cột nguồn/ảnh (SOURCE_COLUMNS, có từ V9) thì các cột đó cũng được upsert; ô rỗng thành NULL.
Giá: CSV vẫn có 3 cột price_web/price_shopee/price_tiktok (dễ sửa tay), nhưng từ V13 DB lưu giá ở bảng
product_channel (1 dòng / sản phẩm / kênh, decisions #32). Script sinh thêm 1 câu upsert vào product_channel.
Thêm kênh: thêm cột price_<kênh> vào CSV, khai báo ở PRICE_COLUMNS và validate_catalog.HEADER.
Chỉ dùng thư viện chuẩn.
"""

import csv
import sys
from pathlib import Path

from validate_catalog import DEFAULT_CSV, HEADER, ROOT, SOURCE_COLUMNS, validate

MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
# Cột giá của CSV -> mã kênh trong bảng sales_channel (V13).
PRICE_COLUMNS = {"price_web": "WEB", "price_shopee": "SHOPEE", "price_tiktok": "TIKTOK"}
INT_COLUMNS = {"size_ml", "pao_months", "stock", *PRICE_COLUMNS}
NULLABLE = {"skin_concerns", "usage_time", "description", *SOURCE_COLUMNS}


def sql_value(column: str, raw: str) -> str:
    if column in INT_COLUMNS:
        return str(int(raw))
    if column == "active":
        return "TRUE" if raw == "true" else "FALSE"
    if column in NULLABLE and not raw.strip():
        return "NULL"
    return "'" + raw.replace("'", "''") + "'"


def render(rows: list[dict[str, str]], source: str) -> str:
    columns = [c for c in HEADER if c not in PRICE_COLUMNS]
    columns += SOURCE_COLUMNS if rows and SOURCE_COLUMNS[0] in rows[0] else []
    updates = ",\n    ".join(f"{c} = EXCLUDED.{c}" for c in columns if c != "sku")
    values = ",\n".join("(" + ", ".join(sql_value(c, row[c]) for c in columns) + ")" for row in rows)
    prices = ",\n".join(
        f"({sql_value('sku', row['sku'])}, '{channel}', {sql_value(column, row[column])})"
        for row in rows for column, channel in PRICE_COLUMNS.items())
    return (
        f"-- Catalog {len(rows)} SKU từ {source} (T-08.2, T-47.1, T-04.1). Owner: LucVT.\n"
        "-- FILE SINH TỰ ĐỘNG bởi tools/catalog/csv_to_sql.py, không sửa tay.\n"
        "-- Upsert theo sku: SKU đã có thì cập nhật, SKU mới thì thêm, không xoá SKU nào.\n"
        "-- Giá theo kênh ghi vào product_channel (V13), upsert theo (product_id, channel_code).\n\n"
        f"INSERT INTO product ({', '.join(columns)}) VALUES\n"
        f"{values}\n"
        "ON CONFLICT (sku) DO UPDATE SET\n"
        f"    {updates},\n"
        "    updated_at = now();\n\n"
        "INSERT INTO product_channel (product_id, channel_code, price)\n"
        "SELECT p.id, v.channel_code, v.price\n"
        "FROM (VALUES\n"
        f"{prices}\n"
        ") AS v (sku, channel_code, price)\n"
        "JOIN product p ON p.sku = v.sku\n"
        "ON CONFLICT (product_id, channel_code) DO UPDATE SET price = EXCLUDED.price;\n"
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
