"""ChatResponse.products: sản phẩm của các dòng thẻ trong câu trả lời, để widget không tải cả catalog (T-43.3). Owner: LucVT."""

from app.agent.product_card import MAX_CARD_PRODUCTS, card_products
from app.rag.search import ProductRow
from tests.test_api import chat
from tests.test_product_card import CAUDALIE


def _row(sku, brand, name, category="sunscreen", skin="oily", web=201_000, shopee=210_000, tiktok=199_000):
    return ProductRow(sku=sku, brand=brand, name=name, category=category, skin_types=skin, skin_concerns="",
                      usage_time="am", price_web=web, price_shopee=shopee, price_tiktok=tiktok)


NIVEA = _row("NIVE-SUN-02", "Nivea", "Nivea Derma Skin Clear UV Fluid SPF50")
ANESSA = _row("ANES-SUN-01", "Anessa", "Anessa Perfect UV Sunscreen Skincare Milk", web=289_000, shopee=295_000)


def test_card_lines_become_products_in_order_without_duplicates():
    reply = (
        "Gợi ý trong shop:\n"
        "1. Anessa — Anessa Perfect UV Sunscreen Skincare Milk — 289.000đ\n"
        "2. **Nivea — Nivea Derma Skin Clear UV Fluid SPF50 — 201.000đ**\n"
        "3. Anessa — Anessa Perfect UV Sunscreen Skincare Milk — 289.000đ\n"
        "4. Hãng Lạ — Sản phẩm không có trong catalog — 100.000đ\n"
        "Bạn thoa trước khi ra nắng 15 phút nhé."
    )
    products = card_products(reply, [NIVEA, ANESSA], "WEB", True)
    assert [p.sku for p in products] == ["ANES-SUN-01", "NIVE-SUN-02"]
    assert products[1].model_dump() == {"sku": "NIVE-SUN-02", "brand": "Nivea",
                                        "name": "Nivea Derma Skin Clear UV Fluid SPF50", "price": 201_000,
                                        "category": "sunscreen"}


def test_price_follows_the_channel_and_the_list_is_capped():
    assert card_products("1. Nivea — Nivea Derma Skin Clear UV Fluid SPF50 — 210.000đ", [NIVEA], "SHOPEE", False)[0].price == 210_000
    rows = [_row(f"SKU-{i}", "Hãng", f"Kem chống nắng mẫu số {i}") for i in range(8)]
    reply = "\n".join(f"{i + 1}. Hãng — Kem chống nắng mẫu số {i} — 201.000đ" for i in range(8))
    assert len(card_products(reply, rows, "WEB", True)) == MAX_CARD_PRODUCTS


def test_plain_text_has_no_products():
    assert card_products("Bạn thoa kem chống nắng mỗi sáng nhé.", [NIVEA], "WEB", True) == []


def test_chat_suggestions_return_products(monkeypatch):
    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: [NIVEA, ANESSA])
    body = chat("kem chống nắng cho da dầu dưới 300k")
    skus = [p["sku"] for p in body["products"]]
    assert skus and set(skus) <= {"NIVE-SUN-02", "ANES-SUN-01"}
    for product in body["products"]:
        assert f"{product['brand']} — {product['name']} — " in body["reply"]


def test_chat_image_question_returns_one_product(monkeypatch):
    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: [CAUDALIE])
    body = chat("cho mình xem ảnh Caudalie Resveratrol-Lift Eye Lifting Balm")
    assert [p["sku"] for p in body["products"]] == ["CAUD-EYE-01"]
    assert body["products"][0]["price"] == 1_290_000


def test_out_of_scope_question_has_no_products(monkeypatch):
    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: [NIVEA])
    body = chat("thời tiết hôm nay thế nào")
    assert body["products"] == []
