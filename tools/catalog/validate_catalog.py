"""Kiểm tra shared/catalog/products.csv trước khi import (T-04.2). Owner: LucVT.

    python tools/catalog/validate_catalog.py                 # file mặc định
    python tools/catalog/validate_catalog.py path/to/x.csv

Kiểm: đủ cột theo schema V4, >= 80 SKU, không trùng SKU, trường bắt buộc không rỗng, giá trị nằm trong
bộ từ vựng ở docs/specs/data-model.md, số hợp lệ, mô tả không có claim y khoa, mọi tên INCI là tên chuẩn trong
shared/catalog/inci_aliases.csv (T-27.4). File có thêm cột nguồn/ảnh
(SOURCE_COLUMNS, catalog thật của T-47.1) thì kiểm thêm barcode, image_key, URL, ghi chú giấy phép.
Mã thoát 1 nếu có lỗi.
Chỉ dùng thư viện chuẩn, không cần venv.
"""

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CSV = ROOT / "shared" / "catalog" / "products.csv"
# Bảng tên INCI chuẩn (T-27.4, decisions #54), sinh bởi tools/catalog/normalize_inci.py.
ALIASES = ROOT / "shared" / "catalog" / "inci_aliases.csv"
MIN_SKU = 80

HEADER = ["sku", "name", "brand", "category", "skin_types", "skin_concerns", "usage_time", "ingredients_inci",
          "size_ml", "pao_months", "price_web", "price_shopee", "price_tiktok", "stock", "active", "description"]
# Cột nguồn và ảnh của catalog thật (T-47.1, V9), mỗi sản phẩm 1 ảnh (decisions #26).
SOURCE_COLUMNS = ["barcode", "source_url", "image_key", "image_source_url", "image_license_note"]
REQUIRED = ["sku", "name", "brand", "category", "skin_types", "ingredients_inci", "size_ml", "pao_months",
            "price_web", "price_shopee", "price_tiktok", "stock", "active"]

CATEGORIES = {"cleanser", "toner", "serum", "moisturizer", "sunscreen", "exfoliant", "spot-treatment", "mask",
              "eye-cream", "lip-care"}
SKIN_TYPES = {"oily", "dry", "combination", "sensitive", "normal"}
SKIN_CONCERNS = {"acne", "oil-control", "pores", "hydration", "dullness", "dark-spots", "aging", "sensitivity",
                 "barrier", "sun-protection"}
USAGE_TIMES = {"am", "pm", "am,pm"}
SKU_PATTERN = re.compile(r"^[A-Z]{3,4}-[A-Z]{3}-\d{2}$")
# Cùng ý với luật no-medical-claim trong shared/policies/common.yaml: mô tả sản phẩm không được nói như thuốc.
MEDICAL_CLAIM = re.compile(r"\b(điều trị|chữa khỏi|chữa|đặc trị|trị (mụn|nám|thâm|sẹo|tàn nhang))\b", re.IGNORECASE)


def load_canonical(path: Path = ALIASES) -> dict[str, list[str]]:
    """tên viết thường -> tên chuẩn (rỗng = token rác). Không có file thì không kiểm INCI chuẩn."""
    if not path.exists():
        return {}
    with path.open(encoding="utf-8", newline="") as f:
        return {r["alias_lower"]: [n.strip() for n in r["canonical"].split("|") if n.strip()] for r in csv.DictReader(f)}


def inci_errors(inci: str, canonical: dict[str, list[str]]) -> list[str]:
    """Mỗi token INCI phải đúng là 1 tên chuẩn (đúng cả cách viết hoa), không lặp trong cùng SKU."""
    problems, seen = [], set()
    for token in csv_set(inci):
        expected = canonical.get(token.lower())
        if expected is None:
            problems.append(f"'{token}' chưa có trong inci_aliases.csv (chạy tools/catalog/normalize_inci.py)")
        elif expected != [token]:
            problems.append(f"'{token}' phải là {' + '.join(expected) or '(bỏ)'}")
        if token.lower() in seen:
            problems.append(f"'{token}' bị lặp")
        seen.add(token.lower())
    return problems


def csv_set(value: str) -> list[str]:
    return [v.strip() for v in value.split(",") if v.strip()]


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames not in (HEADER, HEADER + SOURCE_COLUMNS):
            return [f"header sai: {reader.fieldnames}, cần {HEADER} (có thể thêm {SOURCE_COLUMNS} ở cuối)"]
        has_source = reader.fieldnames == HEADER + SOURCE_COLUMNS
        rows = list(reader)

    if len(rows) < MIN_SKU:
        errors.append(f"chỉ có {len(rows)} SKU, cần >= {MIN_SKU}")

    canonical = load_canonical()
    seen: dict[str, int] = {}
    for line, row in enumerate(rows, start=2):
        sku = row["sku"]
        where = f"dòng {line} ({sku or '?'})"

        missing = [c for c in REQUIRED if not row[c].strip()]
        if missing:
            errors.append(f"{where}: thiếu {missing}")
            continue
        if sku in seen:
            errors.append(f"{where}: trùng SKU với dòng {seen[sku]}")
        seen[sku] = line
        if not SKU_PATTERN.match(sku):
            errors.append(f"{where}: SKU không đúng dạng BRAND-LOAI-NN")
        if len(sku) > 64 or len(row["name"]) > 255 or len(row["ingredients_inci"]) > 4000 \
                or len(row["description"]) > 2000:
            errors.append(f"{where}: vượt độ dài cột")

        if row["category"] not in CATEGORIES:
            errors.append(f"{where}: category '{row['category']}' không hợp lệ")
        if bad := set(csv_set(row["skin_types"])) - SKIN_TYPES:
            errors.append(f"{where}: skin_types không hợp lệ {sorted(bad)}")
        if bad := set(csv_set(row["skin_concerns"])) - SKIN_CONCERNS:
            errors.append(f"{where}: skin_concerns không hợp lệ {sorted(bad)}")
        if row["usage_time"] and row["usage_time"] not in USAGE_TIMES:
            errors.append(f"{where}: usage_time '{row['usage_time']}' không hợp lệ")
        if row["active"] not in ("true", "false"):
            errors.append(f"{where}: active phải là true/false")
        if len(csv_set(row["ingredients_inci"])) < 2:
            errors.append(f"{where}: INCI quá ít thành phần")
        if canonical:
            errors.extend(f"{where}: INCI {p}" for p in inci_errors(row["ingredients_inci"], canonical))

        for col, minimum in (("size_ml", 1), ("pao_months", 1), ("price_web", 0), ("price_shopee", 0),
                             ("price_tiktok", 0), ("stock", 0)):
            try:
                if int(row[col]) < minimum:
                    errors.append(f"{where}: {col} phải >= {minimum}")
            except ValueError:
                errors.append(f"{where}: {col} '{row[col]}' không phải số nguyên")

        if m := MEDICAL_CLAIM.search(row["name"] + " " + row["description"]):
            errors.append(f"{where}: có claim y khoa '{m.group()}'")

        if has_source:
            if not row["barcode"].isdigit():
                errors.append(f"{where}: barcode '{row['barcode']}' phải là chữ số")
            if row["image_key"] != f"{sku}.webp":
                errors.append(f"{where}: image_key phải là '{sku}.webp'")
            for col in ("source_url", "image_source_url"):
                if not row[col].startswith("https://"):
                    errors.append(f"{where}: {col} phải là URL https")
            if not row["image_license_note"].strip():
                errors.append(f"{where}: thiếu image_license_note (nguồn, giấy phép ảnh)")
    return errors


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CSV
    errors = validate(path)
    for e in errors:
        print("LỖI", e)
    if not errors:
        with path.open(encoding="utf-8", newline="") as f:
            print(f"OK: {sum(1 for _ in csv.DictReader(f))} SKU hợp lệ trong {path}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
