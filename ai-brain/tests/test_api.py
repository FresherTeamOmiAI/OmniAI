from fastapi.testclient import TestClient

from app.agent.graph import system_prompt_for
from app.main import app
from app.schemas import ChatRequest

client = TestClient(app)


def chat(text: str, channel: str = "WEB", owned: bool = True, history: list | None = None) -> dict:
    response = client.post(
        "/chat",
        json={
            "message_id": "m1",
            "channel": channel,
            "channel_owned": owned,
            "user_id": "u1",
            "text": text,
            "history": history or [],
        },
    )
    assert response.status_code == 200
    return response.json()


def test_health():
    assert client.get("/health").json()["status"] == "ok"


def test_chat_returns_reply():
    body = chat("Kem chống nắng cho da dầu")
    assert body["reply"]
    assert body["provider"] == "stub"


def test_pregnancy_with_retinol_adds_warning():
    body = chat("Mình đang mang thai, dùng serum retinol được không?")
    assert "pregnancy:retinoid" in body["warnings"]
    assert body["reply"].startswith("Lưu ý:")


def test_prompt_lists_public_product_names_only():
    request = ChatRequest(
        message_id="m1",
        channel="WEB",
        channel_owned=True,
        user_id="u1",
        text="Da dầu buổi tối dùng gì?",
    )
    prompt = system_prompt_for(request, ["Lumi Lab — Dung dịch BHA 2% làm sạch lỗ chân lông"])
    assert "Lumi Lab — Dung dịch BHA 2%" in prompt
    assert "289000" not in prompt
    assert "email" not in prompt


def test_price_question_has_no_invented_numbers():
    body = chat("Kem chống nắng giá bao nhiêu shop?")
    assert "450.000" not in body["reply"]
    assert "kiểm tra" in body["reply"]
    assert "grounding:price" in body["warnings"]


def test_product_question_prefixes_catalog_suggestions(monkeypatch):
    from app.rag.search import ProductRow

    monkeypatch.setattr(
        "app.agent.graph.load_search_rows",
        lambda: [
            ProductRow(
                sku="LUMI-SUN-01",
                brand="Lumi Lab",
                name="Kem chống nắng kiềm dầu SPF50+ PA++++",
                category="sunscreen",
                skin_types="oily,combination",
                skin_concerns="oil-control",
                usage_time="am",
                price_web=289_000,
                price_shopee=299_000,
                price_tiktok=279_000,
            )
        ],
    )
    body = chat("kem chống nắng cho da dầu dưới 300k")
    assert body["reply"].startswith("Gợi ý trong shop:")
    assert "Kem chống nắng kiềm dầu" in body["reply"]


def test_unmatched_request_does_not_invent_a_name(monkeypatch):
    from app.rag.search import ProductRow

    monkeypatch.setattr(
        "app.agent.graph.load_search_rows",
        lambda: [
            ProductRow(
                sku="LUMI-SUN-01",
                brand="Lumi Lab",
                name="Kem chống nắng kiềm dầu SPF50+ PA++++",
                category="sunscreen",
                skin_types="oily",
                skin_concerns="",
                usage_time="am",
                price_web=289_000,
                price_shopee=299_000,
                price_tiktok=279_000,
            )
        ],
    )
    body = chat("nước hoa nam dưới 10k")
    assert "chưa có sản phẩm" in body["reply"]
    assert "Lumi Lab" not in body["reply"]


def _sun_rows():
    from app.rag.search import ProductRow

    def row(sku, brand, name, price, description):
        return ProductRow(
            sku=sku,
            brand=brand,
            name=name,
            category="sunscreen",
            skin_types="oily",
            skin_concerns="sun-protection",
            usage_time="am",
            price_web=price,
            price_shopee=price,
            price_tiktok=price,
            description=description,
        )

    return [
        row("LUMI-SUN-01", "Lumi Lab", "Kem chống nắng kiềm dầu SPF50+ PA++++", 289_000, "Thoa hai đốt ngón tay buổi sáng."),
        row("SEN-SUN-58", "Sen Việt", "Kem chống nắng rau má SPF50", 279_000, "Thoa rau má buổi sáng, làm dịu."),
        row("LUMI-SUN-56", "Lumi Lab", "Xịt chống nắng kiềm dầu SPF50", 255_000, "Xịt lại trong ngày, không xịt mặt."),
        row("VAN-SUN-55", "Vân Khê", "Kem chống nắng lai SPF50+ PA++++", 349_000, "Không được gợi ý khi khách hỏi cách dùng."),
    ]


def test_usage_followup_stays_on_the_suggested_products(monkeypatch):
    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: _sun_rows())
    suggestion = (
        "Gợi ý trong shop:\n"
        "1. Lumi Lab — Kem chống nắng kiềm dầu SPF50+ PA++++ — 289.000đ\n"
        "2. Sen Việt — Kem chống nắng rau má SPF50 — 279.000đ\n"
        "3. Lumi Lab — Xịt chống nắng kiềm dầu SPF50 — 255.000đ"
    )
    history = [
        {"role": "user", "content": "kem chống nắng cho da dầu dưới 300k"},
        {"role": "assistant", "content": suggestion},
    ]
    group = chat("cách sử dụng của bọn này là gì", history=history)
    assert "Gợi ý trong shop" not in group["reply"]
    assert "Thoa hai đốt ngón tay" in group["reply"]
    assert "Thoa rau má" in group["reply"]
    assert "không xịt mặt" in group["reply"]
    assert "VAN-SUN-55" not in group["reply"]

    named = chat(
        "SEN-SUN-58 Kem chống nắng rau má SPF50",
        history=history + [{"role": "user", "content": "cách sử dụng của bọn này là gì"}],
    )
    assert named["reply"].startswith("Cách dùng:")
    assert "SEN-SUN-58" in named["reply"]
    assert "Thoa rau má" in named["reply"]
    assert "VAN-SUN-55" not in named["reply"]
    assert "LUMI-SUN-01" not in named["reply"]

    again = chat(
        "là cách sử dụng của nó ấy",
        history=history
        + [
            {"role": "user", "content": "cách sử dụng của bọn này là gì"},
            {"role": "user", "content": "SEN-SUN-58 Kem chống nắng rau má SPF50"},
        ],
    )
    assert "SEN-SUN-58" in again["reply"]
    assert "Thoa rau má" in again["reply"]
    assert "VAN-SUN-55" not in again["reply"]


def test_usage_without_history_does_not_reuse_another_chat(monkeypatch):
    monkeypatch.setattr("app.agent.graph.load_search_rows", lambda: _sun_rows())
    body = chat("cách sử dụng của bọn này là gì")
    assert "mã hoặc tên" in body["reply"]
    assert "LUMI-SUN-01" not in body["reply"]


def test_price_from_catalog_is_kept():
    body = chat("Kem chống nắng kiềm dầu giá bao nhiêu shop?")
    assert "289.000đ" in body["reply"]
    assert "grounding:price" not in body["warnings"]
