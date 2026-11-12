"""10 ca gợi ý RAG trên catalog thật, không gọi mạng. T-10.2, T-10.3."""

import csv
import json
from pathlib import Path

from app.rag.embed import DIM, cosine, embed
from app.rag.search import ProductRow, load_search_rows, parse_constraints, search_product

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "shared" / "catalog" / "products.csv"
CASES = ROOT / "bench" / "datasets" / "rag.jsonl"


def catalog_rows() -> list[ProductRow]:
    rows = []
    with CATALOG.open(encoding="utf-8", newline="") as handle:
        for raw in csv.DictReader(handle):
            rows.append(
                ProductRow(
                    sku=raw["sku"],
                    brand=raw["brand"],
                    name=raw["name"],
                    category=raw["category"],
                    skin_types=raw["skin_types"],
                    skin_concerns=raw["skin_concerns"],
                    usage_time=raw["usage_time"],
                    price_web=int(raw["price_web"]),
                    price_shopee=int(raw["price_shopee"]),
                    price_tiktok=int(raw["price_tiktok"]),
                    description=raw["description"],
                    active=raw["active"] == "true",
                )
            )
    return rows


def test_embedding_is_stable_and_finds_the_sunscreen():
    sunscreen = embed("kem chống nắng kiềm dầu cho da dầu")
    cleanser = embed("gel rửa mặt trà xanh")
    question = embed("kem chống nắng cho da dầu")
    assert len(question) == DIM
    assert embed("kem chống nắng cho da dầu") == question
    assert cosine(question, sunscreen) > cosine(question, cleanser)


def test_ten_rag_cases_match_constraints():
    products = catalog_rows()
    cases = [json.loads(line) for line in CASES.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(cases) == 10
    for case in cases:
        owned = case["channel"] == "WEB"
        hits = search_product(case["text"], products, channel=case["channel"], owned=owned)
        rule = case["rag"]
        if rule.get("empty"):
            assert hits == [], case["id"]
            continue
        assert hits, case["id"]
        assert len(hits) <= 3
        for hit in hits:
            assert hit.active
            if "skin" in rule:
                assert rule["skin"] in hit.skin_types.split(","), case["id"]
            if "category" in rule:
                assert hit.category == rule["category"], case["id"]
            if "max_price" in rule:
                price = hit.price_web if owned else hit.price_shopee
                assert price <= rule["max_price"], case["id"]
        names = [hit.name for hit in hits]
        if "must_include" in rule:
            assert any(rule["must_include"] in name for name in names), (case["id"], names)
        if "must_exclude" in rule:
            assert all(rule["must_exclude"] not in name for name in names), (case["id"], names)


def test_hidden_product_is_not_suggested():
    hidden = ProductRow(
        sku="MOC-SER-40",
        brand="Mộc Nhiên",
        name="Serum glycolic 8% làm sáng ban đêm",
        category="serum",
        skin_types="normal,oily,combination",
        skin_concerns="dullness",
        usage_time="pm",
        price_web=285000,
        price_shopee=296000,
        price_tiktok=276000,
        active=False,
    )
    hits = search_product("serum glycolic làm sáng da", [hidden])
    assert hits == []


def test_price_question_parses_k_and_dotted_vnd():
    assert parse_constraints("dưới 300k").max_price == 300_000
    assert parse_constraints("dưới 300.000").max_price == 300_000


def test_search_rows_fail_closed_when_database_is_down():
    assert load_search_rows() is None
