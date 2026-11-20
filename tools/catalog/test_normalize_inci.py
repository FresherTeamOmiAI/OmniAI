"""Test luật chuẩn hoá INCI (T-27.4). Chạy: python -m pytest tools/catalog -q"""

import csv

import pytest

import normalize_inci as n
import validate_catalog as v

VOCAB = {"tocopherol", "dimethicone", "butyrospermum", "liquidum"}


@pytest.mark.parametrize("raw, expected", [
    # Đồng nghĩa -> tên EU/ASEAN
    ("Aqua/Water/Eau", ["Aqua"]), ("Water (Aqua)", ["Aqua"]), ("Water/ Aqua / Eau", ["Aqua"]),
    ("Fragrance (Parfum)", ["Parfum"]), ("Parfum / Fragrance", ["Parfum"]), ("Flavor (Aroma)", ["Aroma"]),
    ("Mineral Oil (Paraffinum Liquidum)", ["Paraffinum Liquidum"]), ("Beeswax (Cera Alba)", ["Cera Alba"]),
    ("Mel/Honey", ["Mel"]), ("Sd Alcohol 40-B (Alcohol Denat.)", ["Alcohol Denat."]),
    # Màu: mã CI thắng, bỏ tên màu kiểu Mỹ
    ("CI 77891/Titanium Dioxide", ["CI 77891"]), ("Iron Oxides (CI 77491)", ["CI 77491"]),
    ("CI 19140 / Yellow 5", ["CI 19140"]), ("Ci61565/Green 6", ["CI 61565"]), ("Cl 42090. [PR-0000947]", ["CI 42090"]),
    # Nano
    ("Titanium Dioxide [Nano] / Titanium Dioxide", ["Titanium Dioxide (nano)"]),
    ("Methylene Bis-Benzotriazolyl Tetramethylbutylphenol [Nano]",
     ["Methylene Bis-Benzotriazolyl Tetramethylbutylphenol (nano)"]),
    # %, ppm, mã lô, chú thích
    ("Glycerin 1 %", ["Glycerin"]), ("Homosalate (15%)", ["Homosalate"]), ("Ectoin(100ppm)", ["Ectoin"]),
    ("Benzoic Acid (Code Fil. 2293513/1)", ["Benzoic Acid"]), ("Phenoxyethanol. [PR-014404]", ["Phenoxyethanol"]),
    ("Tocopherol (Vitamin E)", ["Tocopherol"]), ("Lanolin Alcohol (Eucerit®)", ["Lanolin Alcohol"]),
    ("Parfum/Fragrance (F.I.L C260513/3)", ["Parfum"]), ("Ingredients Aqua/Water", ["Aqua"]),
    # Rác
    ("(F.I.L", []), ("Code FIL : B185980/1", []), ("B12107/6)", []), ("Fil1747v00", []), ("201.5", []),
    ("[+/- May Contain", []),
    # Chữ ngắt dòng: ghép nếu từ ghép có trong catalog, không thì giữ gạch nối
    ("Toco - pherol", ["Tocopherol"]), ("PEG - 100 Stearate", ["PEG-100 Stearate"]),
    # Tên copolymer có "/" không bị tách, chỉ bỏ khoảng trắng
    ("Caprylic / capric triglyceride", ["Caprylic/Capric Triglyceride"]),
    ("Centella Asiatica Leaf/Stem Extract", ["Centella Asiatica Leaf/Stem Extract"]),
    # Viết hoa
    ("Butylene GLycol", ["Butylene Glycol"]), ("peg-100 stearate", ["PEG-100 Stearate"]),
    ("Disodium Edta", ["Disodium EDTA"]), ("Melaleuca Alternifolia (Tea Tree) Leaf Oil",
                                           ["Melaleuca Alternifolia (Tea Tree) Leaf Oil"]),
    ("4-terpineol", ["4-Terpineol"]), ("1,2-hexanediol", ["1.2-Hexanediol"]),
    # Tên thực vật giữ ngoặc tên phổ thông ở giữa
    ("Butyrospermum Parkii (Shea) Butter", ["Butyrospermum Parkii (Shea) Butter"]),
])
def test_clean_token(raw, expected):
    assert n.clean_token(raw, VOCAB) == expected


def test_overrides_split_merged_tokens_and_fix_hidden_actives():
    rows = [{"sku": "TST-SER-01", "ingredients_inci": "Water, Salicylicacid, Pentylene glycol salicylic acid, Aqua"}]
    aliases, _ = n.build(rows, n.load_overrides())
    assert n.normalize_inci(rows[0]["ingredients_inci"], aliases) == "Aqua, Salicylic Acid, Pentylene Glycol"


def test_variants_merge_to_the_most_used_spelling():
    rows = [{"sku": f"TST-SER-0{i}", "ingredients_inci": inci} for i, inci in enumerate([
        "Aqua, Helianthus Annuus (Sunflower) Seed Oil", "Aqua, Helianthus Annuus (Sunflower) Seed Oil",
        "Aqua, Helianthus Annuus Seed Oil", "Aqua, Helianthus Annuus Seed Oil / Sunflower Seed Oil"])]
    aliases, _ = n.build(rows, {})
    assert {n.normalize_inci(r["ingredients_inci"], aliases) for r in rows} == {
        "Aqua, Helianthus Annuus (Sunflower) Seed Oil"}


def test_catalog_is_fully_normalized_and_idempotent():
    """Tiêu chí T-27.4: 100% SKU có INCI chuẩn, chạy lại script không đổi gì."""
    _, rows = n.read_rows()
    aliases, _ = n.build(rows, n.load_overrides())
    assert [r["sku"] for r in rows if n.normalize_inci(r["ingredients_inci"], aliases) != r["ingredients_inci"]] == []
    canonical = v.load_canonical()
    assert [(r["sku"], v.inci_errors(r["ingredients_inci"], canonical)) for r in rows
            if v.inci_errors(r["ingredients_inci"], canonical)] == []


def test_alias_file_maps_every_canonical_name_to_itself():
    with n.ALIASES.open(encoding="utf-8", newline="") as f:
        rows = {r["alias_lower"]: r["canonical"] for r in csv.DictReader(f)}
    canonicals = {c.strip() for value in rows.values() for c in value.split("|") if c.strip()}
    assert all(rows.get(c.lower()) == c for c in canonicals)
    assert rows["aqua/water/eau"] == "Aqua" and rows["(f.i.l"] == ""
