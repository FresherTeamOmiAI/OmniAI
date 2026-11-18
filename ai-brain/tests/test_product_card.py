"""Hỏi ảnh thì trả thẻ sản phẩm, không bảo kênh không gửi được hình."""

from app.agent.product_card import asks_for_image, ensure_product_cards, image_reply
from app.rag.search import ProductRow
from app.schemas import HistoryTurn

CAUDALIE = ProductRow(
    sku="CAUD-EYE-01",
    brand="Caudalie",
    name="Caudalie Resveratrol-Lift Eye Lifting Balm",
    category="eye-care",
    skin_types="",
    skin_concerns="",
    usage_time="",
    price_web=1_290_000,
    price_shopee=1_342_000,
    price_tiktok=1_251_000,
)


def test_image_followup_uses_the_product_just_named():
    history = [
        HistoryTurn(role="user", content="giá bán của sản phẩm đắt nhất"),
        HistoryTurn(
            role="assistant",
            content="Sản phẩm đắt nhất là Caudalie — Caudalie Resveratrol-Lift Eye Lifting Balm với giá 1.290.000đ.",
        ),
    ]
    reply = image_reply("không có ảnh à", history, [CAUDALIE], "WEB", True)
    assert reply.startswith("1. Caudalie — Caudalie Resveratrol-Lift Eye Lifting Balm — 1.290.000đ")
    assert "không gửi" not in reply
    assert "ảnh sản phẩm" in reply


def test_plain_price_sentence_gains_a_card():
    reply = ensure_product_cards(
        "Sản phẩm đắt nhất là Caudalie — Caudalie Resveratrol-Lift Eye Lifting Balm với giá trên website là 1.290.000đ.",
        [CAUDALIE],
        "WEB",
        True,
    )
    assert reply.startswith("Gợi ý trong shop:\n1. Caudalie — Caudalie Resveratrol-Lift Eye Lifting Balm — 1.290.000đ")


def test_chat_image_question_returns_the_card(monkeypatch):
    from tests.test_api import chat

    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: [CAUDALIE])
    body = chat(
        "không có ảnh à",
        history=[
            {"role": "assistant", "content": "Sản phẩm đắt nhất là Caudalie Resveratrol-Lift Eye Lifting Balm."},
        ],
    )
    assert "1. Caudalie — Caudalie Resveratrol-Lift Eye Lifting Balm — 1.290.000đ" in body["reply"]
    assert "không gửi" not in body["reply"]
    assert "hỗ trợ gửi hình" not in body["reply"]


def test_casual_hinh_nhu_is_not_an_image_request():
    assert asks_for_image("không có ảnh à")
    assert not asks_for_image("hình như da mình dầu")
