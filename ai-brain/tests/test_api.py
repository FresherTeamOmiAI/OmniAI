from fastapi.testclient import TestClient

from app.main import app

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
    assert body["provider"] == "fake"


def test_pregnancy_with_retinol_adds_warning():
    body = chat("Mình đang mang thai, dùng serum retinol được không?")
    assert "pregnancy:retinoid" in body["warnings"]
    assert body["reply"].startswith("Lưu ý:")
