"""Quy tắc chọn và tự soạn sản phẩm cho catalog thật (T-47.1). Owner: LucVT. Dùng bởi obf_fetch.py.

- BRAND_LIST: hãng có bán ở VN, tiền tố SKU, hạng giá.
- category_of(): map tên + categories_tags của OBF vào 10 danh mục của shop, loại sản phẩm không phải da mặt.
- inci_ok(): INCI đủ dài và là INCI tiếng Anh (không phải bản dịch tiếng Bồ Đào Nha, Đức...).
- annotate(): tự soạn loại da, vấn đề da, buổi dùng, PAO, giá, tồn kho, mô tả cho dòng `annotated_by=auto`.
Các quy tắc là heuristic theo từ khoá, ai thấy sai thì sửa thẳng dòng đó trong selection.csv (discover không ghi đè).
"""

import hashlib
import re
import unicodedata

# (tiền tố SKU, tên hãng chuẩn, hạng giá, alias đã chuẩn hoá bằng slug()).
# Hạng giá: budget (drugstore), mid (dược mỹ phẩm phổ thông), premium, luxury.
BRAND_LIST = [
    ("CERA", "CeraVe", "mid", ["cerave"]),
    ("LRP", "La Roche-Posay", "premium", ["la-roche-posay", "laroche-posay", "la-roche-posay-laboratoire-dermatologique"]),
    ("BIOD", "Bioderma", "mid", ["bioderma"]),
    ("VICH", "Vichy", "premium", ["vichy", "vichy-laboratoires"]),
    ("AVEN", "Avène", "premium", ["avene", "eau-thermale-avene"]),
    ("EUCE", "Eucerin", "premium", ["eucerin"]),
    ("URIA", "Uriage", "mid", ["uriage"]),
    ("SVR", "SVR", "mid", ["svr", "laboratoires-svr"]),
    ("ADER", "A-Derma", "premium", ["a-derma", "aderma"]),
    ("DUCR", "Ducray", "mid", ["ducray"]),
    ("NUXE", "Nuxe", "premium", ["nuxe"]),
    ("CAUD", "Caudalie", "premium", ["caudalie"]),
    ("ORD", "The Ordinary", "budget", ["the-ordinary"]),
    ("INKY", "The Inkey List", "budget", ["the-inkey-list"]),
    ("PAUL", "Paula's Choice", "premium", ["paula-s-choice", "paulas-choice"]),
    ("GARN", "Garnier", "budget", ["garnier", "garnier-skin-naturals", "garnier-skinactive"]),
    ("LORE", "L'Oréal Paris", "budget", ["l-oreal-paris", "l-oreal", "loreal-paris", "loreal"]),
    ("NEUT", "Neutrogena", "budget", ["neutrogena"]),
    ("CETA", "Cetaphil", "mid", ["cetaphil"]),
    ("SIMP", "Simple", "budget", ["simple", "simple-skincare"]),
    ("NIVE", "Nivea", "budget", ["nivea", "nivea-visage"]),
    ("OLAY", "Olay", "mid", ["olay"]),
    ("PNDS", "Pond's", "budget", ["pond-s", "ponds"]),
    ("VASE", "Vaseline", "budget", ["vaseline"]),
    ("AVEE", "Aveeno", "mid", ["aveeno"]),
    ("STIV", "St. Ives", "budget", ["st-ives", "st-ives-swiss-formula"]),
    ("KIEH", "Kiehl's", "luxury", ["kiehl-s", "kiehls"]),
    ("CLIN", "Clinique", "luxury", ["clinique"]),
    ("ESTE", "Estée Lauder", "luxury", ["estee-lauder"]),
    ("LANC", "Lancôme", "luxury", ["lancome"]),
    ("SHIS", "Shiseido", "luxury", ["shiseido"]),
    ("SULW", "Sulwhasoo", "luxury", ["sulwhasoo"]),
    ("COSR", "COSRX", "mid", ["cosrx"]),
    ("SBM", "Some By Mi", "mid", ["some-by-mi", "somebymi"]),
    ("INNI", "Innisfree", "mid", ["innisfree"]),
    ("KLAI", "Klairs", "mid", ["klairs", "dear-klairs"]),
    ("SKIN", "Skin1004", "mid", ["skin1004"]),
    ("LANE", "Laneige", "premium", ["laneige"]),
    ("ANUA", "Anua", "mid", ["anua"]),
    ("TORR", "Torriden", "mid", ["torriden"]),
    ("RNDL", "Round Lab", "mid", ["round-lab"]),
    ("BOJ", "Beauty of Joseon", "mid", ["beauty-of-joseon"]),
    ("ISNT", "Isntree", "mid", ["isntree"]),
    ("PURI", "Purito", "mid", ["purito"]),
    ("GOOD", "Goodal", "mid", ["goodal"]),
    ("MEDI", "Mediheal", "budget", ["mediheal"]),
    ("ETUD", "Etude", "budget", ["etude", "etude-house"]),
    ("MISS", "Missha", "budget", ["missha"]),
    ("TFS", "The Face Shop", "budget", ["the-face-shop"]),
    ("NATR", "Nature Republic", "budget", ["nature-republic"]),
    ("DRJA", "Dr.Jart+", "premium", ["dr-jart", "dr-jart-plus"]),
    ("BANI", "Banila Co", "mid", ["banila-co"]),
    ("HEIM", "Heimish", "mid", ["heimish"]),
    ("NEOG", "Neogen", "mid", ["neogen"]),
    ("IMFR", "I'm From", "mid", ["i-m-from", "im-from"]),
    ("HADA", "Hada Labo", "budget", ["hada-labo", "hada-labo-tokyo"]),
    ("BIOR", "Bioré", "budget", ["biore"]),
    ("SENK", "Senka", "budget", ["senka"]),
    ("ANES", "Anessa", "premium", ["anessa"]),
    ("CURE", "Curél", "mid", ["curel"]),
    ("ROHT", "Rohto", "budget", ["rohto", "melano-cc", "acnes", "rohto-mentholatum"]),
    ("DHC", "DHC", "mid", ["dhc"]),
    ("COCO", "Cocoon", "budget", ["cocoon", "cocoon-vietnam"]),
    ("MIXA", "Mixa", "budget", ["mixa"]),
    ("SEBA", "Sebamed", "mid", ["sebamed"]),
    ("BURT", "Burt's Bees", "budget", ["burt-s-bees", "burts-bees"]),
    ("CARM", "Carmex", "budget", ["carmex"]),
    ("THAY", "Thayers", "mid", ["thayers"]),
    ("BYOM", "Byoma", "mid", ["byoma"]),
]
BRAND_BY_PREFIX = {prefix: name for prefix, name, _, _ in BRAND_LIST}
_BRAND_BY_ALIAS = {alias: (prefix, name, tier) for prefix, name, tier, aliases in BRAND_LIST for alias in aliases}
TIER_FACTOR = {"budget": 0.55, "mid": 1.0, "premium": 1.4, "luxury": 3.0}

CATEGORY_CODE = {"cleanser": "CLE", "toner": "TON", "serum": "SER", "moisturizer": "MOI", "sunscreen": "SUN",
                 "exfoliant": "EXF", "mask": "MSK", "spot-treatment": "SPT", "eye-cream": "EYE", "lip-care": "LIP"}


def slug(text: str) -> str:
    """Bỏ dấu, viết thường, nối bằng '-' (giống brands_tags của OBF)."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def match_brand(*brand_fields: str) -> tuple[str, str, str] | None:
    """(prefix, tên chuẩn, hạng giá) của hãng khớp BRAND_LIST, hoặc None.

    OBF hay ghi cả công ty mẹ ("l-oreal") lẫn hãng con ("garnier", "cerave"), nên L'Oréal chỉ được chọn khi không
    có hãng nào khác khớp.
    """
    found = [_BRAND_BY_ALIAS[key] for field in brand_fields for raw in (field or "").split(",")
             if (key := slug(raw.split(":", 1)[-1])) in _BRAND_BY_ALIAS]
    specific = [b for b in found if b[0] != "LORE"]
    return (specific or found or [None])[0]


# --- Danh mục -------------------------------------------------------------------------------------------------
# Sản phẩm không phải chăm sóc da mặt: gặp từ nào là loại.
_EXCLUDE = re.compile(
    r"shampo|conditioner|apres-shampo|hair|cheveu|haar|capill|deodor|deo-|anti-?perspir|antitranspir|body|corps|"
    r"korper|shower|douche|dusch|bath|bain|hand|mains|haende|foot|pied|fuss|baby|bebe|kids|enfant|child|shav|rasage|"
    r"rasier|toothpaste|dentifrice|mouth|bouche|make-?up-?remover-?wipes|foundation|fond-de-teint|mascara|lipstick|"
    r"rouge-a-levres|nail|vernis|perfume|eau-de-parfum|eau-de-toilette|fragrance|intim|self-?tan|autobronz|"
    r"after-?sun|apres-soleil|tattoo|tatouage|lingette|wipe|tissue-?wipe|soap-?bar|savon|cotton|coton|"
    r"eyeliner|eye-?shadow|eyebrow|concealer|poudre|powder|blush|bronzer|highlighter|lip-?gloss|lip-?liner|"
    r"lipikar|(^|-)bar(-|$)|syndet|vucut|cuerpo|corpo|lotion-corporelle|intensive-care|gluta-hya|petroleum-jelly|"
    r"food|beverage|snack|biscuit|aliment")
_RULES = [  # (danh mục, regex trên slug của tên + categories_tags), thứ tự quan trọng
    ("cleanser", r"make-?up-?remover|demaquill|micell|cleansing-?oil|cleansing-?water"),  # "Eye Make-up Remover"
    ("sunscreen", r"spf|sun-?screen|sunscreen|solaire|soleil|sonnen|photoderm|anthelios|uv-?protect|sun-?cream|sun-?fluid|sun-?milk|sunblock|sun-?protect"),
    ("lip-care", r"(^|-)lips?(-|$)|lip-?balm|lip-?care|levre|lippen|labial|chapstick"),
    ("eye-cream", r"(^|-)eyes?(-|$)|eye-?cream|yeux|regard|augen"),
    ("mask", r"mask|masque|maske|mascarilla|sheet-?mask"),
    ("exfoliant", r"exfoli|peel|scrub|gommage|aha-bha|glycolic|lactic-acid|mandelic"),
    ("toner", r"toner|tonique|(^|-)tonic(o)?(-|$)|tonico|gesichtswasser|essence-water|face-?mist|brume"),
    ("cleanser", r"cleans|nettoy|face-?wash|facial-?wash|wash|micell|demaquill|make-?up-?remover|reinig|"
                 r"cleansing-?oil|huile-demaquillante|gel-moussant|mousse-nettoyante|foaming|purifying-?gel"),
    ("serum", r"serum|booster|ampoule|concentrate|concentre|essence"),
    ("spot-treatment", r"spot|blemish|imperfection|acne|comedo|duo-?plus|effaclar-duo|cleanance-comedomed"),
    ("moisturizer", r"cream|creme|moistur|hydrat|lotion|gel-?creme|day-|night|jour|nuit|soin|fluid|emulsion|"
                    r"baume|balm|feuchtigkeit|gesichtscreme|tagescreme|nachtcreme|face-?care|visage|facial"),
]
_RULES = [(cat, re.compile(pattern)) for cat, pattern in _RULES]
_FACE_CONTEXT = re.compile(r"face|facial|visage|gesicht|skin-?care|soin|serum|creme|cream|moistur|cleans|toner|"
                           r"spf|(^|-)lips?(-|$)|lip-?balm|levre|(^|-)eyes?(-|$)|yeux|mask|masque|peel|micell|lotion|"
                           r"baume|balm|fluid|gel|essence")


def category_of(name: str, categories_tags: str) -> str | None:
    tags = " ".join(t.split(":", 1)[-1] for t in (categories_tags or "").split(","))
    text = slug(f"{name} {tags}")
    if not text or _EXCLUDE.search(text) or not _FACE_CONTEXT.search(text):
        return None
    for category, pattern in _RULES:
        if pattern.search(text):
            return category
    return None


# --- Chất lượng INCI ------------------------------------------------------------------------------------------
MIN_INCI_AUTO = 8
# Từ thường gặp khi ingredients_text là bản dịch hoặc chữ quảng cáo, không phải INCI.
_NON_INCI = re.compile(
    r"\b(agua|água|glicerina|glicerol|álcool|alcool cet|wasser|acqua|gliserin|su|parfüm|duftstoff|aceite|"
    r"olio|olie|vitamine e acetaat|enthält|contains|contient|ingrediënten|bestanddelen|ohne|without|sans parab)\b",
    re.IGNORECASE)


def inci_ok(inci: str) -> bool:
    items = inci.split(", ") if inci else []
    if len(items) < MIN_INCI_AUTO or _NON_INCI.search(inci):
        return False
    if any(len(i) > 90 for i in items) or any(i.startswith("+") for i in items):
        return False
    non_ascii = sum(1 for i in items if re.search(r"[^\x00-\x7F]", i))
    return non_ascii <= len(items) * 0.2


# --- Tự soạn cột của shop -------------------------------------------------------------------------------------
SKIN_ORDER = ["oily", "dry", "combination", "sensitive", "normal"]
_SKIN_KEYWORDS = {
    "oily": r"oil-?control|matt|mat-|purif|sebum|pore|blemish|imperfection|acne|oily|grasse|fettig|clear|effaclar|"
            r"cleanance|sebium|normal-to-oily|anti-?shine",
    "dry": r"dry|seche|trocken|nourish|nourriss|rich|riche|xera|atopi|repair|repar|lipikar|cold-cream|urea|"
           r"very-dry|intense-?hydrat|barrier",
    "sensitive": r"sensitiv|sensible|empfindlich|sooth|apais|calm|toleran|intoleran|cica|centella|sensibio|redness|"
                 r"rougeur|hypoallerg",
    "combination": r"combination|mixte|mischhaut|normal-to-combination",
}
_SKIN_KEYWORDS = {k: re.compile(v) for k, v in _SKIN_KEYWORDS.items()}
_ACTIVES = [  # (regex trên INCI viết thường, tên tiếng Việt trong mô tả, vấn đề da)
    (r"retinol|retinal|retinoate|retinyl", "retinoid", ["aging"]),
    (r"salicylic", "salicylic acid (BHA)", ["acne", "pores"]),
    (r"glycolic", "glycolic acid (AHA)", ["dullness", "pores"]),
    (r"lactic acid", "lactic acid (AHA)", ["dullness"]),
    (r"mandelic", "mandelic acid (AHA)", ["dullness"]),
    (r"azelaic", "azelaic acid", ["acne", "sensitivity"]),
    (r"benzoyl peroxide", "benzoyl peroxide", ["acne"]),
    (r"niacinamide", "niacinamide", ["pores", "oil-control"]),
    (r"ascorbic|ascorbyl", "vitamin C", ["dullness", "dark-spots"]),
    (r"tranexamic|arbutin|kojic", "hoạt chất làm đều màu da", ["dark-spots"]),
    (r"hyaluron", "hyaluronic acid", ["hydration"]),
    (r"ceramide", "ceramide", ["barrier"]),
    (r"panthenol", "panthenol (vitamin B5)", ["barrier"]),
    (r"centella|madecassoside|asiaticoside", "chiết xuất rau má", ["sensitivity"]),
    (r"peptide", "peptide", ["aging"]),
    (r"zinc pca|zinc gluconate", "kẽm", ["oil-control"]),
    (r"urea", "urea", ["hydration"]),
    (r"squalane", "squalane", ["hydration"]),
    (r"thermal spring water|eau thermale", "nước khoáng", ["sensitivity"]),
]
_ACTIVES = [(re.compile(p), label, concerns) for p, label, concerns in _ACTIVES]
_TEXT_CONCERNS = [
    (r"acne|blemish|imperfection|spot", "acne"), (r"pore", "pores"), (r"oil-?control|matt|sebum|anti-?shine", "oil-control"),
    (r"hydrat|moistur|feuchtigkeit", "hydration"), (r"sensitiv|sooth|apais|calm|cica", "sensitivity"),
    (r"dark-?spot|tache|pigment|even-?tone", "dark-spots"), (r"glow|radiance|eclat|bright|lumin", "dullness"),
    (r"anti-?age|anti-?aging|wrinkle|ride|falten|lift|firm|collagen|q10|filler|regener", "aging"),
    (r"repair|repar|barrier", "barrier"),
]
_TEXT_CONCERNS = [(re.compile(p), c) for p, c in _TEXT_CONCERNS]

BASE_PRICE = {  # (giá tầm trung, dung tích tham chiếu ml)
    "cleanser": (300000, 200), "toner": (300000, 200), "serum": (450000, 30), "moisturizer": (400000, 50),
    "sunscreen": (400000, 50), "exfoliant": (350000, 100), "mask": (250000, 75), "spot-treatment": (350000, 30),
    "eye-cream": (450000, 15), "lip-care": (120000, 5)}
DEFAULT_PAO = {"eye-cream": 6, "lip-care": 24}
CATEGORY_VN = {"cleanser": "Sản phẩm làm sạch", "toner": "Nước cân bằng", "serum": "Serum",
               "moisturizer": "Kem dưỡng", "sunscreen": "Kem chống nắng", "exfoliant": "Sản phẩm tẩy tế bào chết",
               "mask": "Mặt nạ", "spot-treatment": "Sản phẩm cho da dễ nổi mụn", "eye-cream": "Kem dưỡng vùng mắt",
               "lip-care": "Sản phẩm dưỡng môi"}
SKIN_VN = {"oily": "da dầu", "dry": "da khô", "combination": "da hỗn hợp", "sensitive": "da nhạy cảm",
           "normal": "da thường"}


def _join_vn(items: list[str]) -> str:
    return items[0] if len(items) == 1 else ", ".join(items[:-1]) + " và " + items[-1]


def _stable_int(text: str, low: int, high: int) -> int:
    return low + int(hashlib.sha1(text.encode()).hexdigest(), 16) % (high - low + 1)


def annotate(barcode: str, name: str, categories_tags: str, inci: str, category: str, size_ml: int,
             tier: str, pao: int | None) -> dict[str, str]:
    """Cột của shop cho một sản phẩm tự lấy (annotated_by=auto)."""
    text = slug(f"{name} {categories_tags}")
    inci_l = inci.lower()
    actives = [(label, concerns) for pattern, label, concerns in _ACTIVES if pattern.search(inci_l)]

    skins = {skin for skin, pattern in _SKIN_KEYWORDS.items() if pattern.search(text)}
    if "oily" in skins:
        skins.add("combination")
    if any(label == "chiết xuất rau má" for label, _ in actives):
        skins.add("sensitive")
    if not skins or skins <= {"sensitive"}:
        skins |= {"normal", "dry", "combination"}
    if category == "lip-care":
        skins = set(SKIN_ORDER)

    concerns: list[str] = []
    if category == "sunscreen" or "spf" in text:
        concerns.append("sun-protection")
    for pattern, concern in _TEXT_CONCERNS:
        if pattern.search(text) and concern not in concerns:
            concerns.append(concern)
    for _, cs in actives:
        concerns += [c for c in cs if c not in concerns]
    if category in ("lip-care", "moisturizer", "toner") and "hydration" not in concerns:
        concerns.append("hydration")
    concerns = concerns[:3]

    retinoid = any(label == "retinoid" for label, _ in actives)
    acid = any("AHA" in label or "BHA" in label for label, _ in actives)
    if category == "sunscreen" or "spf" in text or re.search(r"\bday\b|-day-|jour|tagescreme", text):
        usage = "am"
    elif retinoid or (acid and category in ("exfoliant", "serum", "toner")) or re.search(r"night|nuit|nacht", text):
        usage = "pm"
    else:
        usage = "am,pm"

    if pao is None:
        pao = 6 if retinoid or any(label == "vitamin C" for label, _ in actives) else DEFAULT_PAO.get(category, 12)

    base, ref = BASE_PRICE[category]
    sheet = category == "mask" and (re.search(r"sheet|tissu|tuch|kagit|feuille", text) or size_ml <= 30)
    if sheet:
        price = 35000 * TIER_FACTOR[tier]
    else:
        price = base * TIER_FACTOR[tier] * min(max((size_ml / ref) ** 0.4, 0.6), 1.4)  # dung tích lớn không đắt gấp đôi
    price_web = max(int(round(price, -3)), 29000)

    skin_vn = [SKIN_VN[s] for s in SKIN_ORDER if s in skins]
    labels = [label for label, _ in actives][:3]
    if category == "lip-care":
        description = CATEGORY_VN[category]
    else:
        description = f"{CATEGORY_VN[category]} dành cho {_join_vn(skin_vn)}"
    if labels:
        description += f", có {_join_vn(labels)}"
    description += "."
    if category == "sunscreen" or "spf" in text:
        description += " Dùng buổi sáng, thoa lại khi ở ngoài nắng lâu."
    if retinoid:
        description += " Chỉ dùng buổi tối, không dùng khi mang thai."
    elif acid:
        description += " Ban ngày cần chống nắng."

    return {
        "category": category,
        "skin_types": ",".join(s for s in SKIN_ORDER if s in skins),
        "skin_concerns": ",".join(concerns),
        "usage_time": usage,
        "pao_months": str(pao),
        "price_web": str(price_web),
        "price_shopee": str(int(round(price_web * 1.04, -3))),
        "price_tiktok": str(int(round(price_web * 0.97, -3))),
        "stock": str(_stable_int(barcode, 20, 200)),
        "active": "true",
        "description": description,
    }
