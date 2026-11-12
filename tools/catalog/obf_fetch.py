"""Lấy sản phẩm thật kèm ảnh từ Open Beauty Facts (T-47.1, US-47). Owner: LucVT.

    pip install -r tools/catalog/requirements.txt          # Pillow, chỉ script này cần
    python tools/catalog/obf_fetch.py search cerave         # in ứng viên để chọn barcode
    python tools/catalog/obf_fetch.py build                 # selection.csv -> products_real.csv + ảnh

`build` đọc shared/catalog/real/selection.csv (cột của shop: SKU, loại da, giá, tồn kho, mô tả), lấy từ OBF
tên, hãng, INCI, dung tích, PAO, ảnh mặt trước; ghi shared/catalog/products_real.csv và ảnh WebP
shared/catalog/real/images/<SKU>.webp (cạnh dài 800 px, <= 150 KB). Ảnh và JSON trả về được cache trong
shared/catalog/real/.cache/ (gitignore), chạy lại không gọi API.

Giới hạn của OBF: đọc sản phẩm 15 req/phút, tìm kiếm 10 req/phút trên mỗi IP, vượt có thể bị chặn IP; phải gửi
User-Agent riêng (https://openfoodfacts.github.io/openfoodfacts-server/api/). Dữ liệu theo ODbL, ảnh theo
CC BY-SA 3.0, phải ghi nguồn (docs/catalog/du-lieu-san-pham-that.md).
"""

import csv
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from validate_catalog import HEADER, ROOT, SOURCE_COLUMNS

BASE = "https://world.openbeautyfacts.org"
USER_AGENT = "OmniAI-catalog/1.0 (+https://github.com/FresherTeamOmiAI/OmniAI)"
REAL_DIR = ROOT / "shared" / "catalog" / "real"
CACHE_DIR = REAL_DIR / ".cache"
IMAGE_DIR = REAL_DIR / "images"
SELECTION_CSV = REAL_DIR / "selection.csv"
OUTPUT_CSV = ROOT / "shared" / "catalog" / "products_real.csv"


# name: bỏ trống thì lấy tên trên OBF; điền khi OBF chỉ có tên tiếng Hà Lan/Pháp hoặc tên lỗi.
# pao_months: dùng khi OBF không có periods_after_opening (đa số sản phẩm).
SELECTION_HEADER = ["barcode", "sku", "name", "category", "skin_types", "skin_concerns", "usage_time", "pao_months",
                    "price_web", "price_shopee", "price_tiktok", "stock", "active", "description"]
# Tên hãng chuẩn theo tiền tố SKU: OBF ghi lẫn "Cerave"/"CeraVe" và có bản ghi để trống hãng.
BRANDS = {"CERA": "CeraVe", "LRP": "La Roche-Posay", "BIOD": "Bioderma", "VICH": "Vichy", "AVEN": "Avène",
          "EUCE": "Eucerin", "ORD": "The Ordinary", "GARN": "Garnier", "LORE": "L'Oréal Paris",
          "NEUT": "Neutrogena", "CETA": "Cetaphil", "SIMP": "Simple", "NIVE": "Nivea", "PAUL": "Paula's Choice",
          "COSR": "COSRX", "SBM": "Some By Mi", "INNI": "Innisfree", "KLAI": "Klairs", "ANES": "Anessa",
          "HADA": "Hada Labo", "LANE": "Laneige", "SKIN": "Skin1004", "BIOR": "Bioré", "SENK": "Senka",
          "THAY": "Thayers", "IMFR": "I'm From", "BYOM": "Byoma", "OLAY": "Olay", "ANUA": "Anua",
          "CAUD": "Caudalie", "BURT": "Burt's Bees", "DUCR": "Ducray", "CARM": "Carmex"}
LICENSE_NOTE = ("Ảnh: Open Beauty Facts, CC BY-SA 3.0; dữ liệu: ODbL. "
                "Nhãn hiệu thuộc chủ sở hữu, chỉ dùng cho mục đích học tập")
FIELDS = ("code,product_name,product_name_vi,product_name_en,product_name_fr,brands,ingredients_text,"
          "ingredients_text_en,quantity,product_quantity,product_quantity_unit,periods_after_opening,"
          "image_front_url,categories_tags")

MIN_INTERVAL = {"search": 6.5, "product": 4.5, "image": 1.0}  # giây, dưới hạn mức của OBF
MAX_SIDE = 800
MAX_BYTES = 150 * 1024
MIN_INCI = 5
_last_call: dict[str, float] = {}


def _get(url: str, kind: str) -> bytes:
    wait = MIN_INTERVAL[kind] - (time.monotonic() - _last_call.get(kind, 0.0))
    if wait > 0:
        time.sleep(wait)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
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
    slug = re.sub(r"[^a-z0-9]+", "-", terms.lower()).strip("-")
    return _cached_json(f"search-{slug}.json", f"{BASE}/cgi/search.pl?{query}", "search").get("products", [])


def product(barcode: str) -> dict | None:
    url = f"{BASE}/api/v2/product/{barcode}.json?fields={FIELDS}"
    data = _cached_json(f"product-{barcode}.json", url, "product")
    return data.get("product") if data.get("status") == 1 else None


def name_of(p: dict) -> str:
    for key in ("product_name_vi", "product_name_en", "product_name", "product_name_fr"):
        if (value := (p.get(key) or "").strip()):
            return re.sub(r"\s+", " ", value)
    return ""


def brand_of(p: dict) -> str:
    return (p.get("brands") or "").split(",")[0].strip()


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
    return ", ".join(parts)


def inci_of(p: dict) -> str:
    return normalize_inci(p.get("ingredients_text_en") or p.get("ingredients_text") or "")


def size_ml_of(p: dict) -> int | None:
    unit = (p.get("product_quantity_unit") or "").lower()
    if p.get("product_quantity") and unit in ("ml", "g", ""):
        try:
            return round(float(p["product_quantity"]))
        except (TypeError, ValueError):
            pass
    if m := re.search(r"(\d+(?:[.,]\d+)?)\s*(ml|g)\b", (p.get("quantity") or "").lower()):
        return round(float(m.group(1).replace(",", ".")))
    return None


def pao_of(p: dict) -> int | None:
    m = re.search(r"\d+", p.get("periods_after_opening") or "")
    return int(m.group()) if m else None


def full_image_url(p: dict) -> str:
    url = p.get("image_front_url") or ""
    return re.sub(r"\.(\d+)\.jpg$", ".full.jpg", url) if url else ""


def save_webp(url: str, barcode: str, target: Path) -> None:
    from PIL import Image  # chỉ build cần Pillow

    raw_path = CACHE_DIR / f"img-{barcode}.jpg"
    if not raw_path.exists():
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        raw_path.write_bytes(_get(url, "image"))
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
    with SELECTION_CSV.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != SELECTION_HEADER:
            print(f"LỖI header selection.csv: {reader.fieldnames}, cần {SELECTION_HEADER}")
            return 1
        selection = list(reader)

    rows, problems = [], []
    for i, sel in enumerate(selection, start=1):
        barcode, sku = sel["barcode"].strip(), sel["sku"].strip()
        print(f"[{i}/{len(selection)}] {sku} {barcode}", flush=True)
        p = product(barcode)
        if p is None:
            problems.append(f"{sku} {barcode}: OBF không có sản phẩm")
            continue
        inci, size, image_url = inci_of(p), size_ml_of(p), full_image_url(p)
        pao = pao_of(p) or (int(sel["pao_months"]) if sel["pao_months"].strip() else None)
        name = sel["name"].strip() or name_of(p)
        brand = BRANDS.get(sku.split("-")[0]) or brand_of(p)
        issues = [msg for ok, msg in ((name, "thiếu tên"), (brand, "thiếu hãng"),
                                      (len(inci.split(", ")) >= MIN_INCI if inci else False,
                                       f"INCI < {MIN_INCI} thành phần"),
                                      (size, "thiếu dung tích"), (pao, "thiếu PAO"), (image_url, "thiếu ảnh"))
                  if not ok]
        if issues:
            problems.append(f"{sku} {barcode}: {', '.join(issues)}")
            continue
        save_webp(image_url, barcode, IMAGE_DIR / f"{sku}.webp")
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
    for msg in problems:
        print("BỎ", msg)
    return 1 if problems else 0


def main(argv: list[str]) -> int:
    if len(argv) >= 2 and argv[0] == "search":
        return cmd_search(" ".join(argv[1:]))
    if argv == ["build"]:
        return cmd_build()
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main(sys.argv[1:]))
