"""Sinh migration Flyway từ shared/catalog/products.csv (T-08.2). Owner: LucVT.

    python tools/catalog/csv_to_sql.py V16__catalog_update.sql                 # catalog mặc định
    python tools/catalog/csv_to_sql.py V16__catalog_update.sql path/to/x.csv

Ghi vào backend/src/main/resources/db/migration/<tên file>. Chạy validate_catalog.py trước, có lỗi thì dừng.
Muốn đổi catalog sau khi migration đã merge thì sửa CSV rồi sinh file V<n> MỚI, không sửa file cũ (Flyway kiểm
checksum). Không xoá SKU nào: SKU đã có thì cập nhật, SKU mới thì thêm.

File CSV giữ dạng bảng ngang cho dễ sửa tay. Từ V15 (decisions #35) DB đã chuẩn hoá, nên SQL sinh ra ghi vào:
- brand (thêm hãng mới), product (upsert theo sku, brand_id, use_am/use_pm);
- product_channel: giá theo kênh từ 3 cột price_* (V13, decisions #32). Thêm kênh: thêm cột price_<kênh> vào CSV,
  khai báo ở PRICE_COLUMNS và validate_catalog.HEADER;
- product_skin_type, product_skin_concern, product_ingredient: xoá rồi ghi lại cho các SKU trong file
  (INCI giữ thứ tự, bỏ trùng không phân biệt hoa thường, giống V15); ingredient: thêm thành phần mới;
- product_image (nếu CSV có cột nguồn/ảnh SOURCE_COLUMNS): ảnh chính sort_order 0.
Migration cũ (V5, V10) sinh cho schema trước V15 và không chạy lại; file sinh từ bản này chỉ chạy được sau V15.
Chỉ dùng thư viện chuẩn.
"""

import csv
import sys
from pathlib import Path

from validate_catalog import DEFAULT_CSV, ROOT, SOURCE_COLUMNS, validate

MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
# Cột giá của CSV -> mã kênh trong bảng sales_channel (V13).
PRICE_COLUMNS = {"price_web": "WEB", "price_shopee": "SHOPEE", "price_tiktok": "TIKTOK"}
# Nhóm hoạt chất cho ingredient.active_group, khớp V15 và ai-brain/app/rules/ingredients.py.
ACTIVE_GROUPS = {
    "retinoid": ("retinol", "retinal", "retinaldehyde", "tretinoin", "adapalene", "retinyl palmitate"),
    "aha": ("glycolic acid", "lactic acid", "mandelic acid"),
    "bha": ("salicylic acid",),
    "bpo": ("benzoyl peroxide",),
    "vitamin_c": ("ascorbic acid",),
    "azelaic": ("azelaic acid",),
}


def q(raw: str | None) -> str:
    """Chuỗi SQL; rỗng hoặc None thành NULL."""
    if raw is None or not raw.strip():
        return "NULL"
    return "'" + raw.replace("'", "''") + "'"


def split(value: str) -> list[str]:
    return [part.strip() for part in (value or "").split(",") if part.strip()]


def inci_list(value: str) -> list[str]:
    """Thành phần theo thứ tự, bỏ trùng không phân biệt hoa thường (giữ lần đầu)."""
    seen: dict[str, str] = {}
    for name in split(value):
        seen.setdefault(name.lower(), name)
    return list(seen.values())


def brand_codes(rows: list[dict[str, str]]) -> dict[str, str | None]:
    """code = tiền tố SKU khi hãng chỉ dùng 1 tiền tố và tiền tố đó chỉ thuộc 1 hãng (giống V15)."""
    prefixes: dict[str, set[str]] = {}
    owners: dict[str, set[str]] = {}
    for row in rows:
        prefix = row["sku"].split("-")[0]
        prefixes.setdefault(row["brand"].lower(), set()).add(prefix)
        owners.setdefault(prefix, set()).add(row["brand"].lower())
    codes: dict[str, str | None] = {}
    for row in rows:
        key = row["brand"].lower()
        only = prefixes[key]
        prefix = next(iter(only)) if len(only) == 1 else None
        codes[key] = prefix if prefix and len(owners[prefix]) == 1 and len(prefix) <= 8 else None
    return codes


def values_block(lines: list[str]) -> str:
    return ",\n".join(lines)


def render(rows: list[dict[str, str]], source: str) -> str:
    has_images = bool(rows) and SOURCE_COLUMNS[0] in rows[0]
    skus = ", ".join(q(row["sku"]) for row in rows)
    codes = brand_codes(rows)

    brands: dict[str, str] = {}
    for row in rows:
        brands.setdefault(row["brand"].lower(), row["brand"])
    brand_values = [f"({q(codes[key])}, {q(name)})" for key, name in brands.items()]

    product_values = []
    for row in rows:
        usage = row["usage_time"]
        product_values.append("(" + ", ".join([
            q(row["sku"]), q(row["name"]), q(row["brand"]), q(row["category"]), q(row["description"]),
            str(int(row["size_ml"])), str(int(row["pao_months"])), str(int(row["stock"])),
            "TRUE" if row["active"] == "true" else "FALSE",
            "TRUE" if usage in ("am", "am,pm") else "FALSE", "TRUE" if usage in ("pm", "am,pm") else "FALSE",
            q(row.get("barcode")) if has_images else "NULL", q(row.get("source_url")) if has_images else "NULL",
        ]) + ")")

    price_values = [f"({q(row['sku'])}, '{channel}', {int(row[column])})"
                    for row in rows for column, channel in PRICE_COLUMNS.items()]
    skin_values = [f"({q(row['sku'])}, {q(code.lower())})" for row in rows for code in split(row["skin_types"])]
    concern_values = [f"({q(row['sku'])}, {q(code.lower())})" for row in rows for code in split(row["skin_concerns"])]

    ingredients: dict[str, str] = {}
    inci_values = []
    for row in rows:
        for position, name in enumerate(inci_list(row["ingredients_inci"]), start=1):
            ingredients.setdefault(name.lower(), name)
            inci_values.append(f"({q(row['sku'])}, {position}, {q(name.lower())})")
    ingredient_values = [f"({q(name)})" for name in ingredients.values()]

    group_updates = "".join(
        f"UPDATE ingredient SET active_group = '{group}' WHERE active_group IS NULL AND lower(inci_name) IN ("
        + ", ".join(q(name) for name in names) + ");\n"
        for group, names in ACTIVE_GROUPS.items())

    parts = [
        f"-- Catalog {len(rows)} SKU từ {source} (T-08.2, T-47.1, T-04.1). Owner: LucVT.\n"
        "-- FILE SINH TỰ ĐỘNG bởi tools/catalog/csv_to_sql.py, không sửa tay. Chỉ chạy được sau V15 (decisions #35).\n"
        "-- Upsert theo sku, không xoá SKU nào. Loại da, vấn đề da, INCI của các SKU trong file được ghi lại toàn bộ.\n\n",
        "INSERT INTO brand (code, name) VALUES\n" + values_block(brand_values) + "\nON CONFLICT DO NOTHING;\n\n",
        "INSERT INTO product (sku, name, brand_id, category, description, size_ml, pao_months, stock, active,"
        " use_am, use_pm, barcode, source_url)\n"
        "SELECT v.sku, v.name, b.id, v.category, v.description, v.size_ml, v.pao_months, v.stock, v.active,"
        " v.use_am, v.use_pm, v.barcode, v.source_url\n"
        "FROM (VALUES\n" + values_block(product_values) + "\n"
        ") AS v (sku, name, brand, category, description, size_ml, pao_months, stock, active, use_am, use_pm,"
        " barcode, source_url)\n"
        "JOIN brand b ON lower(b.name) = lower(v.brand)\n"
        "ON CONFLICT (sku) DO UPDATE SET\n"
        "    name = EXCLUDED.name, brand_id = EXCLUDED.brand_id, category = EXCLUDED.category,"
        " description = EXCLUDED.description,\n"
        "    size_ml = EXCLUDED.size_ml, pao_months = EXCLUDED.pao_months, stock = EXCLUDED.stock,"
        " active = EXCLUDED.active,\n"
        "    use_am = EXCLUDED.use_am, use_pm = EXCLUDED.use_pm, barcode = EXCLUDED.barcode,"
        " source_url = EXCLUDED.source_url,\n"
        "    updated_at = now();\n\n",
        "INSERT INTO product_channel (product_id, channel_code, price)\n"
        "SELECT p.id, v.channel_code, v.price\n"
        "FROM (VALUES\n" + values_block(price_values) + "\n) AS v (sku, channel_code, price)\n"
        "JOIN product p ON p.sku = v.sku\n"
        "ON CONFLICT (product_id, channel_code) DO UPDATE SET price = EXCLUDED.price;\n\n",
        f"DELETE FROM product_skin_type WHERE product_id IN (SELECT id FROM product WHERE sku IN ({skus}));\n"
        "INSERT INTO product_skin_type (product_id, skin_type_code)\n"
        "SELECT p.id, v.code\nFROM (VALUES\n" + values_block(skin_values) + "\n) AS v (sku, code)\n"
        "JOIN product p ON p.sku = v.sku;\n\n",
        f"DELETE FROM product_skin_concern WHERE product_id IN (SELECT id FROM product WHERE sku IN ({skus}));\n",
    ]
    if concern_values:
        parts.append(
            "INSERT INTO product_skin_concern (product_id, skin_concern_code)\n"
            "SELECT p.id, v.code\nFROM (VALUES\n" + values_block(concern_values) + "\n) AS v (sku, code)\n"
            "JOIN product p ON p.sku = v.sku;\n")
    parts += [
        "\nINSERT INTO ingredient (inci_name) VALUES\n" + values_block(ingredient_values) + "\nON CONFLICT DO NOTHING;\n",
        group_updates,
        f"\nDELETE FROM product_ingredient WHERE product_id IN (SELECT id FROM product WHERE sku IN ({skus}));\n"
        "INSERT INTO product_ingredient (product_id, position, ingredient_id)\n"
        "SELECT p.id, v.position, i.id\nFROM (VALUES\n" + values_block(inci_values) + "\n) AS v (sku, position, lower_name)\n"
        "JOIN product p ON p.sku = v.sku\n"
        "JOIN ingredient i ON lower(i.inci_name) = v.lower_name;\n",
    ]
    if has_images:
        image_values = [
            f"({q(row['sku'])}, {q(row['image_key'])}, {q(row['image_source_url'])}, {q(row['image_license_note'])})"
            for row in rows if row["image_key"].strip()]
        if image_values:
            parts.append(
                "\nINSERT INTO product_image (product_id, sort_order, storage_key, source_url, license_note, is_primary)\n"
                "SELECT p.id, 0, v.storage_key, v.source_url, v.license_note, TRUE\n"
                "FROM (VALUES\n" + values_block(image_values) + "\n) AS v (sku, storage_key, source_url, license_note)\n"
                "JOIN product p ON p.sku = v.sku\n"
                "ON CONFLICT (product_id, sort_order) DO UPDATE SET storage_key = EXCLUDED.storage_key,"
                " source_url = EXCLUDED.source_url, license_note = EXCLUDED.license_note, is_primary = TRUE;\n")
    return "".join(parts)


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
