"""Catalog thật từ Open Beauty Facts (T-47.1, US-47). Owner: LucVT.

    pip install -r tools/catalog/requirements.txt          # Pillow, chỉ script này cần
    python tools/catalog/obf_fetch.py dump                  # tải dump CSV của OBF (khoảng 20 MB) vào cache
    python tools/catalog/obf_fetch.py discover              # thêm sản phẩm mới từ dump vào selection.csv
    python tools/catalog/obf_fetch.py build                 # selection.csv -> shared/catalog/products.csv + ảnh
    python tools/catalog/obf_fetch.py search cerave         # tra cứu nhanh qua API (in barcode, INCI, ảnh...)

Luồng: dump -> discover -> build -> validate_catalog.py -> csv_to_sql.py V<n>__... -> upload_images.py.

- `discover` lọc dump theo hãng có bán ở VN và 10 danh mục (obf_annotate.py), chỉ giữ sản phẩm có ảnh, dung tích,
  INCI tiếng Anh >= 8 thành phần; tự soạn cột của shop và ghi dòng `annotated_by=auto`. Chỉ THÊM dòng, không sửa
  dòng đã có, SKU đã gán không đổi (DB upsert theo sku).
- `build` lấy tên, INCI, dung tích, PAO, ảnh từ cache API (nếu đã có) hoặc từ dump, chỉ gọi API khi barcode không có
  trong dump; ghi shared/catalog/products.csv và ảnh WebP shared/catalog/real/images/<SKU>.webp (<= 800 px,
  <= 150 KB). Dump, JSON và ảnh gốc được cache ở shared/catalog/real/.cache/ (gitignore).

OBF: đọc sản phẩm 15 req/phút, tìm kiếm 10 req/phút trên mỗi IP, cần hơn vài trăm sản phẩm thì phải dùng dump; luôn gửi
User-Agent riêng (https://openfoodfacts.github.io/openfoodfacts-server/api/). Dữ liệu ODbL, ảnh CC BY-SA 3.0, phải
ghi nguồn khi hiển thị (docs/catalog/du-lieu-san-pham-that.md).
"""

import csv
import gzip
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

from obf_annotate import (BRAND_BY_PREFIX, CATEGORY_CODE, annotate, category_of, inci_ok, match_brand, slug)
from validate_catalog import DEFAULT_CSV, HEADER, ROOT, SOURCE_COLUMNS

BASE = "https://world.openbeautyfacts.org"
DUMP_URL = "https://static.openbeautyfacts.org/data/en.openbeautyfacts.org.products.csv.gz"
USER_AGENT = "OmniAI-catalog/1.0 (+https://github.com/FresherTeamOmiAI/OmniAI)"
REAL_DIR = ROOT / "shared" / "catalog" / "real"
CACHE_DIR = REAL_DIR / ".cache"
DUMP_PATH = CACHE_DIR / "obf-products.csv.gz"
IMAGE_DIR = REAL_DIR / "images"
SELECTION_CSV = REAL_DIR / "selection.csv"
OUTPUT_CSV = DEFAULT_CSV  # shared/catalog/products.csv: catalog thật thay hẳn catalog hư cấu (decisions #30)

# name: tên hiển thị (dòng manual là tên tiếng Anh chuẩn, dòng auto là tên trên OBF); bỏ trống thì lấy từ OBF.
# pao_months: dùng khi OBF không có periods_after_opening (đa số sản phẩm).
# annotated_by: manual = người soạn và duyệt; auto = discover tự soạn, sửa tay thoải mái.
SELECTION_HEADER = ["barcode", "sku", "name", "category", "skin_types", "skin_concerns", "usage_time", "pao_months",
                    "price_web", "price_shopee", "price_tiktok", "stock", "active", "description", "annotated_by"]
LICENSE_NOTE = ("Ảnh: Open Beauty Facts, CC BY-SA 3.0; dữ liệu: ODbL. "
                "Nhãn hiệu thuộc chủ sở hữu, chỉ dùng cho mục đích học tập")
FIELDS = ("code,product_name,product_name_vi,product_name_en,product_name_fr,brands,ingredients_text,"
          "ingredients_text_en,quantity,product_quantity,product_quantity_unit,periods_after_opening,"
          "image_front_url,categories_tags")

MIN_INTERVAL = {"search": 6.5, "product": 4.5, "image": 1.0, "dump": 0.0}  # giây, dưới hạn mức của OBF
MAX_SIDE = 800
MAX_BYTES = 150 * 1024
MIN_INCI = 5
SIZE_RANGE = (3, 1000)  # ml; ngoài khoảng này là mẫu thử hoặc can lớn
_last_call: dict[str, float] = {}


def _get(url: str, kind: str) -> bytes:
    wait = MIN_INTERVAL[kind] - (time.monotonic() - _last_call.get(kind, 0.0))
    if wait > 0:
        time.sleep(wait)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                _last_call[kind] = time.monotonic()
                return response.read()
        except urllib.error.HTTPError as error:
            _last_call[kind] = time.monotonic()
            if error.code not in (429, 503) or attempt == 2:
                raise
            print(f"  OBF trả {error.code}, chờ 60 s rồi thử lại", file=sys.stderr)
            time.sleep(60)
    raise RuntimeError("unreachable")


def _cached_json(name: str, url: str, kind: str) -> dict:
    path = CACHE_DIR / name
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    data = json.loads(_get(url, kind))
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def search(terms: str, page_size: int = 100) -> list[dict]:
    query = urllib.parse.urlencode({"search_terms": terms, "json": 1, "page_size": page_size, "fields": FIELDS})
    return _cached_json(f"search-{slug(terms)}.json", f"{BASE}/cgi/search.pl?{query}", "search").get("products", [])


def api_product(barcode: str) -> dict | None:
    url = f"{BASE}/api/v2/product/{barcode}.json?fields={FIELDS}"
    data = _cached_json(f"product-{barcode}.json", url, "product")
    return data.get("product") if data.get("status") == 1 else None


# --- Dump -----------------------------------------------------------------------------------------------------
def _from_dump(row: dict) -> dict:
    """Dòng dump (TSV) -> dict cùng khoá với API v2 để các hàm *_of() dùng chung."""
    return {"code": row.get("code", ""), "product_name": row.get("product_name", ""),
            "brands": row.get("brands_en") or row.get("brands_tags", ""),
            "brands_tags": row.get("brands_tags", ""), "ingredients_text": row.get("ingredients_text", ""),
            "quantity": row.get("quantity", ""), "product_quantity": row.get("product_quantity", ""),
            "image_front_url": row.get("image_url", ""), "categories_tags": row.get("categories_tags", "")}


def iter_dump():
    if not DUMP_PATH.exists():
        raise SystemExit("Chưa có dump, chạy `obf_fetch.py dump` trước")
    csv.field_size_limit(2**31 - 1)
    with gzip.open(DUMP_PATH, "rt", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            yield _from_dump(row)


# --- Trích trường ---------------------------------------------------------------------------------------------
def name_of(p: dict) -> str:
    for key in ("product_name_vi", "product_name_en", "product_name", "product_name_fr"):
        if (value := (p.get(key) or "").strip()):
            return re.sub(r"\s+", " ", value)[:255]
    return ""


def brand_of(p: dict) -> str:
    return (p.get("brands") or "").split(",")[0].split(":", 1)[-1].strip()


# Viết tắt INCI giữ nguyên chữ hoa khi đổi chuỗi VIẾT HOA sang dạng Title Case.
INCI_ACRONYMS = {"PEG", "PPG", "BHT", "BHA", "EDTA", "HCL", "CI", "PCA", "DNA", "SPF", "UV", "II", "III", "AHA"}


def _inci_case(item: str) -> str:
    if not item.isupper():
        return item[0].upper() + item[1:]
    words = re.split(r"(\W+)", item.lower())
    return "".join(w.upper() if w.upper() in INCI_ACRONYMS else w.capitalize() for w in words)


def normalize_inci(text: str) -> str:
    """Chuỗi thành phần OBF -> INCI nối bằng ", " như products.csv.

    Bỏ mã lô ở đầu ("909353 06 AQUA"), tiền tố kiểu "Ingredients/ Ingrédients:", chú thích "*organic";
    dấu chấm dùng làm dấu phân cách được tách như dấu phẩy; "1,2-Hexanediol" thành "1.2-Hexanediol" vì cột INCI
    tách theo dấu phẩy (data-model.md); chuỗi VIẾT HOA đổi sang Title Case.
    """
    text = text.split("\n")[0]
    head = text[:60]
    if ":" in head:                                      # "Ingredients/ Ingrédients: AQUA ..."
        text = text[head.rindex(":") + 1:]
    text = re.sub(r"^\s*\d[\d\s]*\s(?=[A-Za-z])", "", text)  # mã lô in trên bao bì
    text = re.sub(r"(\d),(\d)", r"\1.\2", text)
    text = re.sub(r"\.\s+\*.*$", "", text)              # chú thích cuối kiểu ". *organic"
    text = text.replace("*", "").replace("_", "")
    parts = []
    for raw in re.split(r"[,;]|\.\s+(?=[A-Za-z(])", text):
        item = re.sub(r"\s+", " ", raw).strip(" .")
        if item:
            parts.append(_inci_case(item))
    return ", ".join(parts)[:4000]


def inci_of(p: dict) -> str:
    return normalize_inci(p.get("ingredients_text_en") or p.get("ingredients_text") or "")


def size_ml_of(p: dict) -> int | None:
    quantity = (p.get("quantity") or "").lower()
    value = None
    if m := re.search(r"(\d+(?:[.,]\d+)?)\s*(ml|g|gr|grams?)\b", quantity):
        value = float(m.group(1).replace(",", "."))
    elif m := re.search(r"(\d+(?:[.,]\d+)?)\s*(fl\.?\s*)?oz\b", quantity):
        value = float(m.group(1).replace(",", ".")) * 29.57
    elif p.get("product_quantity") and (p.get("product_quantity_unit") or "").lower() in ("ml", "g", ""):
        try:
            value = float(p["product_quantity"])
        except (TypeError, ValueError):
            value = None
    return round(value) if value and value >= 1 else None


def pao_of(p: dict) -> int | None:
    m = re.search(r"\d+", p.get("periods_after_opening") or "")
    return int(m.group()) if m else None


def image_urls_of(p: dict) -> tuple[str, str]:
    """(ảnh bản đầy đủ, ảnh 400 px) của ảnh mặt trước. Sản phẩm mới tải bản 400 px cho nhẹ."""
    url = p.get("image_front_url") or ""
    if not url:
        return "", ""
    return re.sub(r"\.(\d+|full)\.jpg$", ".full.jpg", url), re.sub(r"\.(\d+|full)\.jpg$", ".400.jpg", url)


def save_webp(p: dict, barcode: str, target: Path) -> str:
    """Ghi ảnh WebP, trả về URL ảnh gốc đã dùng (ghi vào image_source_url)."""
    from PIL import Image  # chỉ build cần Pillow

    raw_path, url_path = CACHE_DIR / f"img-{barcode}.jpg", CACHE_DIR / f"img-{barcode}.url"
    full, small = image_urls_of(p)
    if raw_path.exists():
        url = url_path.read_text(encoding="utf-8").strip() if url_path.exists() else full
    else:
        url = small
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        raw_path.write_bytes(_get(url, "image"))
        url_path.write_text(url, encoding="utf-8")
    with Image.open(raw_path) as img:
        img = img.convert("RGB")
        img.thumbnail((MAX_SIDE, MAX_SIDE))
        for quality in (82, 72, 62, 52, 42):
            buffer = io.BytesIO()
            img.save(buffer, "WEBP", quality=quality, method=6)
            if buffer.tell() <= MAX_BYTES:
                break
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(buffer.getvalue())
    return url


# --- selection.csv --------------------------------------------------------------------------------------------
def read_selection() -> list[dict]:
    with SELECTION_CSV.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        fields = reader.fieldnames or []
    if fields == SELECTION_HEADER[:-1]:  # file cũ trước khi có annotated_by: 80 dòng đều do người soạn
        for row in rows:
            row["annotated_by"] = "manual"
    elif fields != SELECTION_HEADER:
        raise SystemExit(f"LỖI header selection.csv: {fields}, cần {SELECTION_HEADER}")
    return rows


def write_selection(rows: list[dict]) -> None:
    with SELECTION_CSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=SELECTION_HEADER, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


# --- Lệnh -----------------------------------------------------------------------------------------------------
def cmd_dump() -> int:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Tải {DUMP_URL} ...", flush=True)
    DUMP_PATH.write_bytes(_get(DUMP_URL, "dump"))
    print(f"Ghi {DUMP_PATH.relative_to(ROOT)} ({DUMP_PATH.stat().st_size // 1024 // 1024} MB)")
    return 0


def cmd_discover() -> int:
    selection = read_selection()
    known_barcodes = {r["barcode"] for r in selection}
    used_numbers: dict[tuple[str, str], int] = {}
    for r in selection:
        prefix, code, number = r["sku"].split("-")
        used_numbers[(prefix, code)] = max(used_numbers.get((prefix, code), 0), int(number))

    best: dict[tuple[str, str, int], tuple[int, dict]] = {}  # (prefix, tên chuẩn hoá, ml) -> (số INCI, ứng viên)
    for p in iter_dump():
        barcode = p["code"].strip()
        if not barcode.isdigit() or barcode in known_barcodes or not p["image_front_url"]:
            continue
        brand = match_brand(p["brands_tags"], p["brands"])
        name = name_of(p)
        if brand is None or not name:
            continue
        category = category_of(name, p["categories_tags"])
        size = size_ml_of(p)
        if category is None or size is None or not SIZE_RANGE[0] <= size <= SIZE_RANGE[1]:
            continue
        inci = inci_of(p)
        if not inci_ok(inci):
            continue
        key = (brand[0], slug(name), size)
        count = len(inci.split(", "))
        if key not in best or count > best[key][0]:
            best[key] = (count, {"barcode": barcode, "name": name, "brand": brand, "category": category,
                                 "size": size, "inci": inci, "tags": p["categories_tags"]})

    added, skipped_full = [], 0
    for _, c in sorted(best.values(), key=lambda x: (x[1]["brand"][0], x[1]["category"], slug(x[1]["name"]))):
        prefix, _, tier = c["brand"]
        code = CATEGORY_CODE[c["category"]]
        number = used_numbers.get((prefix, code), 0) + 1
        if number > 99:  # SKU_PATTERN chỉ có 2 chữ số
            skipped_full += 1
            continue
        used_numbers[(prefix, code)] = number
        shop = annotate(c["barcode"], c["name"], c["tags"], c["inci"], c["category"], c["size"], tier, None)
        added.append({"barcode": c["barcode"], "sku": f"{prefix}-{code}-{number:02d}", "name": c["name"],
                      **shop, "annotated_by": "auto"})

    write_selection(selection + added)
    print(f"Thêm {len(added)} sản phẩm vào {SELECTION_CSV.relative_to(ROOT)} (tổng {len(selection) + len(added)})")
    if skipped_full:
        print(f"Bỏ {skipped_full} sản phẩm vì hết số SKU (> 99 cho một hãng + danh mục)")
    print("Theo danh mục:", dict(Counter(r["category"] for r in added).most_common()))
    print("Theo hãng:", dict(Counter(r["sku"].split("-")[0] for r in added).most_common()))
    return 0


def cmd_search(terms: str) -> int:
    products = search(terms)
    print(f"{len(products)} kết quả cho '{terms}' (INCI = số thành phần, ảnh, dung tích, PAO)")
    for p in products:
        inci = inci_of(p)
        count = len(inci.split(", ")) if inci else 0
        flags = f"inci={count:<3} img={'y' if p.get('image_front_url') else '-'} " \
                f"ml={size_ml_of(p) or '-'} pao={pao_of(p) or '-'}"
        print(f"{p.get('code', ''):<14} {flags:<32} {brand_of(p)} | {name_of(p)}")
    return 0


def cmd_build() -> int:
    selection = read_selection()
    wanted = {r["barcode"] for r in selection if not (CACHE_DIR / f"product-{r['barcode']}.json").exists()}
    dump = {p["code"]: p for p in iter_dump() if p["code"] in wanted} if wanted else {}

    rows, problems = [], []
    for i, sel in enumerate(selection, start=1):
        barcode, sku = sel["barcode"].strip(), sel["sku"].strip()
        if i % 50 == 0 or i == len(selection):
            print(f"[{i}/{len(selection)}] {sku}", flush=True)
        p = api_product(barcode) if barcode not in dump else dump[barcode]
        if p is None:
            problems.append((sel, f"{sku} {barcode}: OBF không có sản phẩm"))
            continue
        inci, size = inci_of(p), size_ml_of(p)
        pao = pao_of(p) or (int(sel["pao_months"]) if sel["pao_months"].strip() else None)
        name = sel["name"].strip() or name_of(p)
        brand = BRAND_BY_PREFIX.get(sku.split("-")[0]) or brand_of(p)
        issues = [msg for ok, msg in ((name, "thiếu tên"), (brand, "thiếu hãng"),
                                      (len(inci.split(", ")) >= MIN_INCI if inci else False,
                                       f"INCI < {MIN_INCI} thành phần"),
                                      (size, "thiếu dung tích"), (pao, "thiếu PAO"),
                                      (p.get("image_front_url"), "thiếu ảnh"))
                  if not ok]
        if issues:
            problems.append((sel, f"{sku} {barcode}: {', '.join(issues)}"))
            continue
        try:
            image_url = save_webp(p, barcode, IMAGE_DIR / f"{sku}.webp")
        except (urllib.error.URLError, OSError) as error:
            problems.append((sel, f"{sku} {barcode}: tải ảnh lỗi ({error})"))
            continue
        rows.append({**{c: sel.get(c, "").strip() for c in HEADER},
                     "name": name, "brand": brand, "ingredients_inci": inci,
                     "size_ml": str(size), "pao_months": str(pao),
                     "barcode": barcode, "source_url": f"{BASE}/product/{barcode}", "image_key": f"{sku}.webp",
                     "image_source_url": image_url, "image_license_note": LICENSE_NOTE})

    with OUTPUT_CSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=HEADER + SOURCE_COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"Ghi {len(rows)} SKU vào {OUTPUT_CSV.relative_to(ROOT)}, ảnh ở {IMAGE_DIR.relative_to(ROOT)}")
    for _, msg in problems:
        print("BỎ", msg)
    # Dòng manual là dữ liệu đã duyệt: thiếu dòng nào là lỗi. Dòng auto hỏng thì chỉ báo.
    return 1 if any(sel["annotated_by"] == "manual" for sel, _ in problems) else 0


def main(argv: list[str]) -> int:
    if len(argv) >= 2 and argv[0] == "search":
        return cmd_search(" ".join(argv[1:]))
    commands = {"dump": cmd_dump, "discover": cmd_discover, "build": cmd_build}
    if len(argv) == 1 and argv[0] in commands:
        return commands[argv[0]]()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main(sys.argv[1:]))
