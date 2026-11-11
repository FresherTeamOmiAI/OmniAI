from fastapi.testclient import TestClient

from app.agent.graph import system_prompt_for
from app.main import app
from app.schemas import ChatRequest

client = TestClient(app)


def chat(text: str, channel: str = "WEB", owned: bool = True) -> dict:
    response = client.post(
        "/chat",
        json={"message_id": "m1", "channel": channel, "channel_owned": owned, "user_id": "u1", "text": text},
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


def test_price_from_catalog_is_kept():
    body = chat("Kem chống nắng kiềm dầu giá bao nhiêu shop?")
    assert "289.000đ" in body["reply"]
    assert "grounding:price" not in body["warnings"]
