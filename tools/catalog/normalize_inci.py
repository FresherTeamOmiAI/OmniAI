"""Chuẩn hoá tên INCI của catalog theo danh pháp EU/ASEAN (T-27.4, US-27, decisions #54). Owner: LucVT.

    python tools/catalog/normalize_inci.py            # ghi products.csv, inci_aliases.csv, in báo cáo
    python tools/catalog/normalize_inci.py --migration V25__inci_alias_more.sql   # sinh thêm migration MỚI
    python tools/catalog/normalize_inci.py --check    # chỉ kiểm: mã thoát 1 nếu products.csv còn tên chưa chuẩn
    python tools/catalog/normalize_inci.py --report   # chỉ in báo cáo, không ghi file

Dữ liệu INCI lấy từ Open Beauty Facts (OCR nhãn) nên lẫn mã lô (F.I.L ...), %, ppm, chữ ngắt dòng
("Toco - pherol"), tên đồng nghĩa ("Aqua/Water/Eau", "Fragrance (Parfum)"), viết hoa lộn xộn. Script:
  1. Áp ghi đè tay trong shared/catalog/inci_overrides.csv (ca luật không đoán được: token dính nhau, lỗi OCR).
  2. Áp luật tự động cho từng token (clean_token).
  3. Gộp các cách viết của cùng 1 tên (bỏ ngoặc tên phổ thông ở giữa để so), chọn cách viết dùng nhiều nhất.
  4. Ghi shared/catalog/inci_aliases.csv (tên gốc viết thường -> tên chuẩn, trống = bỏ) và viết lại cột
     ingredients_inci của products.csv. Có --migration thì sinh migration từ bảng alias (V24 sinh như vậy; lần sau
     đặt tên V<n> mới, KHÔNG ghi đè migration đã chạy). SQL sinh ra chạy lại được (IF NOT EXISTS, ON CONFLICT).
Quy ước tên chuẩn: Aqua, Parfum, Aroma, Paraffinum Liquidum, Cera Alba, Mel, CI xxxxx (màu), "(nano)";
"1.2-Hexanediol" giữ dấu chấm vì cột INCI tách bằng dấu phẩy. Chỉ dùng thư viện chuẩn.
"""

from __future__ import annotations

import argparse
import collections
import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRODUCTS = ROOT / "shared" / "catalog" / "products.csv"
OVERRIDES = ROOT / "shared" / "catalog" / "inci_overrides.csv"
ALIASES = ROOT / "shared" / "catalog" / "inci_aliases.csv"
MIGRATIONS = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"

# Tên đồng nghĩa -> tên EU/ASEAN. Khoá viết thường.
SPECIAL: dict[str, str] = {}
for canonical, names in {
    "Aqua": ["aqua", "water", "eau", "purified water", "aqua purificata"],
    "Parfum": ["parfum", "fragrance", "fragance", "perfume"],
    "Aroma": ["aroma", "flavor", "flavour"],
    "Paraffinum Liquidum": ["paraffinum liquidum", "mineral oil", "huile minerale", "huile minérale"],
    "Cera Alba": ["cera alba", "beeswax", "cire d'abeille"],
    "Mel": ["mel", "honey", "miel"],
    "Alcohol Denat.": ["alcohol denat", "alcohol denat.", "sd alcohol 40-b", "sd alcohol 40", "alcohol denatured"],
    "Avene Aqua": ["avene aqua", "avene thermal spring water", "avène thermal spring water"],
    "Cera Microcristallina": ["cera microcristallina", "microcrystalline wax", "microcrystalinne wax"],
}.items():
    for n in names:
        SPECIAL[n] = canonical

# Chất có cả tên hoá học và mã màu: EU ghi mã CI khi dùng làm chất màu.
CI_PREFERRED = {"iron oxides", "iron oxide", "titanium dioxide"}
US_COLOR = re.compile(r"^(?:red|yellow|blue|green|orange|violet)\s*\d+(?:\s*lake)?$", re.I)
CI_CODE = re.compile(r"^CI \d{5}$")

# Từ cuối (head) cho biết "A / B" là 2 cách gọi 1 chất, không phải tên copolymer kiểu "Caprylic/Capric Triglyceride".
HEADS = {
    "oil", "butter", "extract", "wax", "cera", "water", "aqua", "eau", "parfum", "fragrance", "aroma", "flavor",
    "honey", "mel", "juice", "powder", "starch", "sterols", "alba", "liquidum", "beeswax", "dioxide", "oxides",
    "microcristallina", "gum",
}

# Viết hoa nguyên từ. Còn lại: hoa chữ đầu (Title Case từng đoạn chữ).
UPPER = {
    "peg", "ppg", "edta", "bht", "bha", "ci", "vp", "va", "pvm", "ma", "hdi", "ipdi", "dna",
    "pca", "dmdm", "uv", "pvp", "mipa", "tdi", "smdi", "gmp", "tbhq",
    "np", "ap", "eop", "ns", "eos",  # Ceramide NP, AP, EOP...
}
# Viết hoa chỉ khi đứng trước "-" (TEA-Lauryl Sulfate), còn lại là từ thường (Tea Tree).
UPPER_BEFORE_HYPHEN = {"tea", "dea", "mea"}

# Nhóm hoạt chất của ai-brain (app/rules/ingredients.py) + azelaic (V15). KHÔNG thêm dẫn xuất ở đây (decisions #54).
ACTIVE_GROUPS = {
    "retinoid": ["retinol", "retinal", "retinaldehyde", "tretinoin", "adapalene", "retinyl palmitate"],
    "aha": ["glycolic acid", "lactic acid", "mandelic acid"],
    "bha": ["salicylic acid"],
    "bpo": ["benzoyl peroxide"],
    "vitamin_c": ["ascorbic acid"],
    "azelaic": ["azelaic acid"],
}
DERIVATIVE = re.compile(r"retin|ascorb|salicyl|glycolic|lactic|mandelic|azela|gluconolactone|lactobionic|bakuchiol", re.I)

# Mảnh rác do OCR/tách dấu phẩy: mã lô, số điện thoại, câu tiếng nước ngoài.
JUNK = [
    re.compile(p, re.I) for p in (
        r"^[a-z]{0,2}\d{3,}[/\-]?[\dv.]*\)?$",       # B12107/6), Z70038504/1, 201.5
        r"^\(?\s*(?:code\s*)?f\.?\s*[ij]\.?\s*l\b",  # (F.I.L, Code FIL : B185980/1, F.J.L. 0524.V00
        r"^\(?\s*fil\.?\s*[a-z]?\d",                  # Fil 1747, (Fil B161311/2), Fil1747v00
        r"^\(?code f\)?$", r"^\(n\d+/[a-z]\)$", r"^\[?\+/-\s*may contain$",
        r"\bart\.?\s*-?\s*no\b", r"\blote\b", r"\bdial\b", r"\banalyse\b", r"volontaires", r"www\.", r"^\d[\d\s.]*$",
    )
]

ANNOTATIONS = [
    (re.compile(p, re.I), r) for p, r in (
        (r"[^\x00-\x7FÀ-ɏ]+", " "),                         # chữ không phải Latin, ®, ™
        (r"\[\s*(?:pr|bi)[-\s]?\d+\s*\]", " "),                       # [PR-0000947], [Bi 446]
        (r"\(\s*bi\s*\d+\s*\).*$", " "),                              # (Bi 538) Sun Active Defense...
        (r"\[\s*\d+/[a-z]\s*\].*$", " "),                             # [0388/A] Ingrédients issus de...
        (r"\(?\s*(?:code\s*)?f\.?\s*[ij]\.?\s*l\b.*$", " "),          # (Code F.I.L ...) tới hết token
        (r"\(?\s*f\.\s*[ij]\s*[./]?\s*l.*$", " "),                   # (F.I.Lz296646/1), (F.I/L B170603/1)
        (r"\(?\s*code\s*fil\b.*$", " "),                              # (Code Fil. 2293513/1)
        (r"\|", " "),
        (r"\bfl?il\.?\s*[a-z]?\d.*$", " "),                           # FIL.1745, Fil B52455/1), Fll 2700...
        (r"\(\s*\d{3}/\d{3}\s*\)", " "),                              # (165/017)
        (r"\(?\s*\d[\d.,]*\s*ppm\s*\)?", " "),                        # (100ppm), (5 ppm)
        (r"\(?\s*\d[\d.,]*\s*%\s*(?:v/v)?\s*\)?", " "),               # 3%, (15%), 3.6% v/v
        (r"questions\?.*$", " "), (r"\bagent purifiant\b", " "), (r"\bingr[eé]dients issus.*$", " "),
        (r"^\[\+/-\s*may contain\s*:?\s*", ""),                       # [+/- May Contain : CI 77891
        (r"^\d[\d\s]*\s-\s", ""),                                     # 2050519 10 - Aqua/Water (giữ 1-Methyl..., 4-Terpineol)
        (r"^(?:inactive\s+)?ingredients?\b\s*(?:\([^)]*\))?\s*:?\s*", ""),  # Ingredients(50mL & 20mL) Water
    )
]


def load_overrides(path: Path = OVERRIDES) -> dict[str, list[str]]:
    """raw_lower -> danh sách tên chuẩn ("|" ngăn nhiều tên; trống = bỏ token)."""
    if not path.exists():
        return {}
    result: dict[str, list[str]] = {}
    with path.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            key = row["raw_lower"].strip().lower()
            result[key] = [n.strip() for n in row["canonical"].split("|") if n.strip()]
    return result


def fix_case(name: str) -> str:
    """Title Case từng đoạn chữ, giữ mã viết hoa (PEG, CI, EDTA...), "(nano)", "sh-" của peptide."""
    def word(m: re.Match[str]) -> str:
        w = m.group(0).lower()
        if w in UPPER or (w in UPPER_BEFORE_HYPHEN and m.string[m.end():m.end() + 1] == "-"):
            return w.upper()
        if w == "nano":
            return "nano"
        if w == "hcl":
            return "HCl"
        if w == "sh" and m.string[m.end():m.end() + 1] == "-":
            return "sh"
        return w[0].upper() + w[1:]
    return re.sub(r"[A-Za-zÀ-ɏ]+", word, name)


def join_split_words(text: str, vocab: set[str]) -> str:
    """Chữ bị ngắt dòng: "Toco - pherol" -> "Tocopherol" nếu từ ghép có trong catalog, không thì "PEG-100"."""
    def repl(m: re.Match[str]) -> str:
        left, right = m.group(1), m.group(2)
        if (left + right).lower() in vocab:
            return left + right
        return f"{left}-{right}"
    return re.sub(r"([A-Za-zÀ-ɏ0-9]+) - ([A-Za-zÀ-ɏ0-9]+)", repl, text)


def balance(text: str) -> str:
    """Sửa ngoặc lệch do OCR: "[" như "(", ngoặc đóng thừa bỏ, ngoặc mở thiếu đóng thì đóng ở cuối."""
    text = text.replace("[", "(").replace("]", ")")
    out, depth = [], 0
    for ch in text:
        if ch == "(":
            depth += 1
        elif ch == ")":
            if depth == 0:
                continue
            depth -= 1
        out.append(ch)
    s = "".join(out) + ")" * depth
    s = re.sub(r"\(\s*\)", "", s)
    s = re.sub(r"\s*\(\s*", " (", s)
    s = re.sub(r"\s*\)\s*", ") ", s)
    return s


def split_top(text: str, sep: str = "/") -> list[str]:
    """Tách theo sep ở ngoài ngoặc."""
    parts, depth, cur = [], 0, []
    for ch in text:
        depth += ch == "("
        depth -= ch == ")"
        if ch == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    parts.append("".join(cur))
    return [p.strip() for p in parts]


def strip_parens(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"\([^)]*\)", " ", text)).strip()


def ci(text: str) -> str:
    return re.sub(r"\bc[il]\s*(\d{5})\b", lambda m: f"CI {m.group(1)}", text, flags=re.I)


def special(text: str) -> str | None:
    return SPECIAL.get(strip_parens(text).lower().rstrip("."))


def resolve(text: str) -> str:
    """Đưa 1 tên (đã sạch rác) về tên EU: xử lý "A / B", "A (B)" ở cuối, tên đồng nghĩa."""
    text = text.strip()
    if special(text) and "(" not in text:
        return special(text)  # type: ignore[return-value]

    parts = [p for p in split_top(text) if p]
    if len(parts) > 1:
        heads = strip_parens(parts[0]).lower().split()
        synonym = (heads and heads[-1] in HEADS) or any(special(p) for p in parts) \
            or any(CI_CODE.match(ci(strip_parens(p))) for p in parts)
        if synonym:
            parts = [p for p in parts if not US_COLOR.match(strip_parens(p))]
            for p in parts:
                if special(p):
                    return special(p)  # type: ignore[return-value]
            codes = [ci(strip_parens(p)) for p in parts if CI_CODE.match(ci(strip_parens(p)))]
            if codes:
                return codes[0]
            return resolve(parts[0])
        text = "/".join(parts)

    m = re.match(r"^(.*?)\s*\(([^()]*)\)$", text)
    if m and m.group(1):
        head, inner = m.group(1).strip(), m.group(2).strip()
        if inner.lower() == "nano":
            return f"{resolve(head)} (nano)"
        if special(head) or special(inner):
            return special(head) or special(inner)  # type: ignore[return-value]
        inner_ci = ci(inner)
        if CI_CODE.match(inner_ci) and strip_parens(head).lower() in CI_PREFERRED:
            return inner_ci
        if US_COLOR.match(inner) or CI_CODE.match(inner_ci):
            return resolve(head)
        if "(" not in head and not re.search(r"\)\s+\S", text):
            return resolve(head)  # "Tocopherol (Vitamin E)" -> "Tocopherol"
        return resolve(head)
    return text


def clean_token(raw: str, vocab: set[str] | None = None) -> list[str]:
    """1 token thô -> 0 hoặc 1 tên chuẩn (chưa gộp toàn catalog). Rác trả []."""
    s = raw.strip()
    for pattern, repl in ANNOTATIONS:
        s = pattern.sub(repl, s)
    s = join_split_words(s, vocab or set())
    s = balance(s)
    s = re.sub(r"\s+", " ", s).strip(" .*:;-")
    s = re.sub(r"\)(?=[A-Za-z])", ") ", s)
    if not s or any(p.search(s) for p in JUNK) or not re.search(r"[A-Za-z]{2}", s):
        return []
    s = ci(s)
    s = resolve(s)
    s = re.sub(r"\s*/\s*", "/", s) if "/" in s else s
    s = re.sub(r"\b1,2-", "1.2-", s)
    s = re.sub(r"\s+", " ", s).strip(" .*:;-")
    if s.lower() == "alcohol denat":
        s = "Alcohol Denat."
    if not s or any(p.search(s) for p in JUNK):
        return []
    return [fix_case(s) if s not in SPECIAL.values() else s]


def tokens(inci: str) -> list[str]:
    return [t.strip() for t in inci.split(",") if t.strip()]


def group_key(name: str) -> str:
    """Khoá gộp: bỏ ngoặc tên phổ thông ở giữa (trừ "(nano)"), so không phân biệt hoa thường."""
    nano = "(nano)" in name
    key = strip_parens(name).lower()
    return key + (" (nano)" if nano else "")


def build(rows: list[dict[str, str]], overrides: dict[str, list[str]]) -> tuple[dict[str, list[str]], collections.Counter]:
    """Bảng alias: tên gốc viết thường -> danh sách tên chuẩn đã gộp toàn catalog."""
    raw_counts: collections.Counter[str] = collections.Counter()
    for r in rows:
        for t in tokens(r["ingredients_inci"]):
            raw_counts[t.lower()] += 1
    vocab = {w for t in raw_counts for w in re.findall(r"[a-zà-ɏ0-9]+", t)}

    first: dict[str, list[str]] = {}
    for raw in raw_counts:
        if raw in overrides:
            first[raw] = overrides[raw]
            continue
        # Ghi đè cũng khớp được tên SAU khi làm sạch (vd. "Toco Pherol", token dính "Sodium Benzoate Citric Acid").
        out: list[str] = []
        for n in clean_token(raw, vocab):
            out.extend(overrides.get(n.lower(), [n]))
        first[raw] = out

    # Gộp các cách viết, chọn cách viết dùng nhiều nhất (hoà thì cách có ngoặc, rồi theo chữ cái).
    form_count: collections.Counter[str] = collections.Counter()
    for raw, names in first.items():
        for n in names:
            form_count[n] += raw_counts[raw]
    groups: dict[str, list[str]] = collections.defaultdict(list)
    for form in form_count:
        groups[group_key(form)].append(form)
    best = {key: sorted(forms, key=lambda f: (-form_count[f], "(" not in f, f))[0] for key, forms in groups.items()}

    aliases: dict[str, list[str]] = {}
    for raw, names in first.items():
        out: list[str] = []
        for n in names:
            c = best[group_key(n)]
            if c.lower() not in {o.lower() for o in out}:
                out.append(c)
        aliases[raw] = out
    for c in best.values():  # tên chuẩn cũng là 1 bí danh của chính nó (admin gõ "aqua" -> "Aqua")
        aliases.setdefault(c.lower(), [c])
    return aliases, form_count


def normalize_inci(inci: str, aliases: dict[str, list[str]]) -> str:
    out: list[str] = []
    seen: set[str] = set()
    for t in tokens(inci):
        for n in aliases.get(t.lower(), [t]):
            if n.lower() not in seen:
                seen.add(n.lower())
                out.append(n)
    return ", ".join(out)


def sql_literal(s: str | None) -> str:
    return "NULL" if s is None else "'" + s.replace("'", "''") + "'"


def migration_sql(aliases: dict[str, list[str]]) -> str:
    values = []
    for raw in sorted(aliases):
        names = aliases[raw]
        if not names:
            values.append(f"({sql_literal(raw)}, 1, NULL)")
        for i, n in enumerate(names, 1):
            values.append(f"({sql_literal(raw)}, {i}, {sql_literal(n)})")
    groups = "\n".join(
        f"UPDATE ingredient SET active_group = '{g}' WHERE lower(inci_name) IN ({', '.join(sql_literal(n) for n in names)});"
        for g, names in ACTIVE_GROUPS.items()
    )
    return (HEADER_SQL + ",\n".join(values)
            + "\nON CONFLICT (alias_lower, seq) DO UPDATE SET canonical = EXCLUDED.canonical;\n" + BODY_SQL + groups + "\n")


HEADER_SQL = """-- T-27.4 (US-27, decisions #54): chuẩn hoá tên INCI theo danh pháp EU/ASEAN. Owner: LucVT.
-- FILE SINH TỰ ĐỘNG bởi tools/catalog/normalize_inci.py từ shared/catalog/inci_aliases.csv. Không sửa tay.
-- Quy ước: Aqua, Parfum, Aroma, Paraffinum Liquidum, Cera Alba, Mel, CI xxxxx, "(nano)"; bỏ mã lô F.I.L, %, ppm;
--          gộp các cách viết của cùng 1 tên. ingredient_alias: tên gốc (viết thường) -> tên chuẩn, canonical NULL =
--          token rác (bỏ khỏi sản phẩm); 1 token dính nhiều tên thì nhiều dòng theo seq.
-- Chuyển TẠI CHỖ theo bảng alias (không dựng lại từ CSV) để giữ sản phẩm admin đã sửa trên DB dùng chung;
-- tên lạ không có trong bảng thì giữ nguyên. product.updated_at không đổi (sửa dữ liệu, không phải admin sửa).
-- ProductService cũng đọc ingredient_alias để tên admin gõ mới vào luôn chuẩn.

CREATE TABLE IF NOT EXISTS ingredient_alias (
    alias_lower VARCHAR(400) NOT NULL,
    seq         SMALLINT     NOT NULL,
    canonical   VARCHAR(200),
    PRIMARY KEY (alias_lower, seq),
    CONSTRAINT ck_ingredient_alias_lower CHECK (alias_lower = lower(alias_lower)),
    CONSTRAINT ck_ingredient_alias_seq CHECK (seq >= 1)
);

INSERT INTO ingredient_alias (alias_lower, seq, canonical) VALUES
"""

BODY_SQL = """
-- (a) Tên chuẩn chưa có thì thêm.
INSERT INTO ingredient (inci_name)
SELECT DISTINCT ON (lower(a.canonical)) a.canonical
FROM ingredient_alias a
WHERE a.canonical IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ingredient i WHERE lower(i.inci_name) = lower(a.canonical))
ORDER BY lower(a.canonical), a.canonical;

-- (b)-(d) Sản phẩm có thành phần nằm trong bảng alias: đổi sang tên chuẩn, bỏ rác, bỏ trùng (giữ vị trí đầu),
--         đánh lại position từ 1 theo thứ tự cũ.
CREATE TEMP TABLE pi_new ON COMMIT DROP AS
SELECT pi.product_id,
       pi.position * 100 + COALESCE(a.seq, 0) AS ord,
       COALESCE(ci.id, pi.ingredient_id)       AS ingredient_id,
       (a.alias_lower IS NOT NULL AND a.canonical IS NULL) AS dropped
FROM product_ingredient pi
JOIN ingredient i ON i.id = pi.ingredient_id
LEFT JOIN ingredient_alias a ON a.alias_lower = lower(i.inci_name)
LEFT JOIN ingredient ci ON lower(ci.inci_name) = lower(a.canonical)
WHERE pi.product_id IN (
    SELECT x.product_id FROM product_ingredient x JOIN ingredient y ON y.id = x.ingredient_id
    WHERE EXISTS (SELECT 1 FROM ingredient_alias z WHERE z.alias_lower = lower(y.inci_name)));

DELETE FROM product_ingredient WHERE product_id IN (SELECT product_id FROM pi_new);

INSERT INTO product_ingredient (product_id, position, ingredient_id)
SELECT product_id, row_number() OVER (PARTITION BY product_id ORDER BY first_ord), ingredient_id
FROM (SELECT product_id, ingredient_id, min(ord) AS first_ord
      FROM pi_new WHERE NOT dropped GROUP BY product_id, ingredient_id) t;

-- (e) Tên cũ không còn sản phẩm nào dùng và đã được đổi sang tên khác: xoá.
DELETE FROM ingredient i
WHERE NOT EXISTS (SELECT 1 FROM product_ingredient p WHERE p.ingredient_id = i.id)
  AND EXISTS (SELECT 1 FROM ingredient_alias a WHERE a.alias_lower = lower(i.inci_name)
              AND (a.canonical IS NULL OR lower(a.canonical) <> lower(i.inci_name)));

-- (f) Cách viết hoa chuẩn ("Butylene GLycol" -> "Butylene Glycol").
UPDATE ingredient i SET inci_name = a.canonical
FROM ingredient_alias a
WHERE a.alias_lower = lower(i.inci_name) AND a.seq = 1 AND a.canonical IS NOT NULL
  AND lower(a.canonical) = lower(i.inci_name) AND i.inci_name <> a.canonical;

-- Nhóm hoạt chất: đúng danh sách của ai-brain/app/rules/ingredients.py (+ azelaic như V15), chạy lại sau khi gộp.
"""


def read_rows() -> tuple[list[str], list[dict[str, str]]]:
    with PRODUCTS.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        return list(reader.fieldnames or []), list(reader)


def report(rows: list[dict[str, str]], aliases: dict[str, list[str]]) -> list[str]:
    before = {t.lower() for r in rows for t in tokens(r["ingredients_inci"])}
    after = {n.lower() for r in rows for n in tokens(normalize_inci(r["ingredients_inci"], aliases))}
    changed = sum(r["ingredients_inci"] != normalize_inci(r["ingredients_inci"], aliases) for r in rows)
    dropped = sorted(raw for raw, names in aliases.items() if not names and raw in before)
    derivatives = sorted({n for n in after if DERIVATIVE.search(n)}
                         - {n for names in ACTIVE_GROUPS.values() for n in names})
    lines = [
        f"SKU: {len(rows)}, đổi INCI: {changed}",
        f"Tên khác nhau (không phân biệt hoa thường): {len(before)} -> {len(after)}",
        f"Token bỏ (rác): {len(dropped)}",
        "Dẫn xuất hoạt chất KHÔNG gắn nhóm (để ChienPX/chuyên môn quyết): " + "; ".join(derivatives),
    ]
    return lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report", action="store_true")
    parser.add_argument("--migration", help="tên file migration MỚI cần sinh, vd. V25__inci_alias_more.sql")
    args = parser.parse_args()

    header, rows = read_rows()
    if args.check:
        aliases, _ = build(rows, load_overrides())
        bad = [r["sku"] for r in rows if normalize_inci(r["ingredients_inci"], aliases) != r["ingredients_inci"]]
        if bad:
            print(f"{len(bad)} SKU còn INCI chưa chuẩn: {', '.join(bad[:20])}")
            return 1
        print(f"OK: {len(rows)} SKU có INCI chuẩn")
        return 0

    aliases, _ = build(rows, load_overrides())
    for line in report(rows, aliases):
        print(line)
    if args.report:
        return 0

    for r in rows:
        r["ingredients_inci"] = normalize_inci(r["ingredients_inci"], aliases)
    with PRODUCTS.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)

    # Bảng alias gồm cả tên gốc trước khi viết lại CSV: chạy lại script trên CSV đã chuẩn vẫn giữ các bí danh cũ.
    merged = load_aliases()
    merged.update(aliases)
    with ALIASES.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(["alias_lower", "canonical"])
        for raw in sorted(merged):
            writer.writerow([raw, " | ".join(merged[raw])])
    print(f"Đã ghi {PRODUCTS.relative_to(ROOT)}, {ALIASES.relative_to(ROOT)} ({len(merged)} dòng)")
    if args.migration:
        target = MIGRATIONS / args.migration
        if target.exists():
            print(f"{target.name} đã có: không ghi đè migration (Flyway sẽ báo sai checksum). Đặt tên V<n> mới.")
            return 1
        target.write_text(migration_sql(merged), encoding="utf-8", newline="\n")
        print(f"Đã sinh {target.name}")
    return 0


def load_aliases() -> dict[str, list[str]]:
    if not ALIASES.exists():
        return {}
    with ALIASES.open(encoding="utf-8", newline="") as f:
        return {row["alias_lower"]: [n.strip() for n in row["canonical"].split("|") if n.strip()]
                for row in csv.DictReader(f)}


if __name__ == "__main__":
    sys.exit(main())
