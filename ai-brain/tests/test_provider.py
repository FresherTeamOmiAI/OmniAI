import json

import httpx
import pytest

from app.catalog_access import require_config
from app.llm.factory import get_provider
from app.llm.gemini import GeminiProvider
from app.llm.groq import GroqProvider
from app.schemas import ChatRequest


def _request() -> ChatRequest:
    return ChatRequest(
        message_id="m1",
        channel="WEB",
        channel_owned=True,
        user_id="u1",
        text="Da dầu dùng kem chống nắng nào?",
    )


def test_default_provider_is_gemini(monkeypatch):
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.delenv("LLM_MODEL", raising=False)
    for unset in (None, "", "  "):
        if unset is None:
            monkeypatch.delenv("LLM_PROVIDER", raising=False)
        else:
            monkeypatch.setenv("LLM_PROVIDER", unset)
        provider = get_provider()
        assert isinstance(provider, GeminiProvider)
        assert provider.model == "gemini-3.6-flash"


def test_fake_provider_is_gone(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "fake")
    with pytest.raises(ValueError, match="đã bị bỏ"):
        get_provider()


def test_default_provider_requires_api_key(monkeypatch):
    monkeypatch.delenv("LLM_PROVIDER", raising=False)
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    with pytest.raises(ValueError, match="LLM_API_KEY"):
        get_provider()


def test_brain_requires_catalog_database_url(monkeypatch):
    monkeypatch.delenv("CHATBOT_DB_URL", raising=False)
    with pytest.raises(ValueError, match="CHATBOT_DB_URL"):
        require_config()
    monkeypatch.setenv("CHATBOT_DB_URL", "postgresql://chatbot_reader:x@localhost:5432/omniai")
    require_config()


def test_provider_switches_with_env(monkeypatch):
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.setenv("LLM_PROVIDER", "GEMINI")
    monkeypatch.delenv("LLM_MODEL", raising=False)
    gemini = get_provider()
    assert isinstance(gemini, GeminiProvider)
    assert gemini.name == "gemini"
    assert gemini.model == "gemini-3.6-flash"

    monkeypatch.setenv("LLM_PROVIDER", "groq")
    monkeypatch.setenv("LLM_MODEL", "qwen/qwen3.8-27b")
    groq = get_provider()
    assert isinstance(groq, GroqProvider)
    assert groq.name == "groq"
    assert groq.model == "qwen/qwen3.8-27b"


def test_real_provider_requires_api_key(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "gemini")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    with pytest.raises(ValueError, match="LLM_API_KEY"):
        get_provider()


def test_unknown_provider_is_rejected(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    with pytest.raises(ValueError, match="chưa được hỗ trợ"):
        get_provider()


def test_gemini_generate_sends_prompt_and_skips_thoughts():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["key"] = request.headers["x-goog-api-key"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {"thought": True, "text": "suy nghĩ nội bộ"},
                                {"text": "Dạ da dầu nên chọn kem chống nắng kiềm dầu ạ."},
                            ]
                        }
                    }
                ]
            },
        )

    provider = GeminiProvider("gemini-3.6-flash", "test-key", transport=httpx.MockTransport(handler))
    answer = provider.generate(_request(), "Bạn là trợ lý của shop.")

    assert answer == "Dạ da dầu nên chọn kem chống nắng kiềm dầu ạ."
    assert seen["key"] == "test-key"
    assert "gemini-3.6-flash:generateContent" in seen["url"]
    assert seen["body"]["systemInstruction"]["parts"][0]["text"] == "Bạn là trợ lý của shop."
    assert seen["body"]["contents"][0]["parts"][0]["text"] == "Da dầu dùng kem chống nắng nào?"
    assert seen["body"]["generationConfig"]["thinkingConfig"] == {"thinkingLevel": "low"}
    assert "test-key" not in answer


def test_groq_gpt_oss_lowers_reasoning_effort():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = json.loads(request.content)
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={"choices": [{"message": {"content": "Dạ shop đã nhận câu hỏi."}}]})

    provider = GroqProvider("openai/gpt-oss-20b", "test-key", transport=httpx.MockTransport(handler))
    answer = provider.generate(_request(), "prompt kênh")

    assert answer == "Dạ shop đã nhận câu hỏi."
    assert seen["body"]["reasoning_effort"] == "low"
    assert seen["body"]["tool_choice"] == "none"
    assert seen["body"]["messages"][0]["content"] == "prompt kênh"
    assert seen["auth"] == "Bearer test-key"


def test_groq_qwen_does_not_set_reasoning_effort():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"choices": [{"message": {"content": "Dạ được ạ."}}]})

    provider = GroqProvider("qwen/qwen3.8-27b", "test-key", transport=httpx.MockTransport(handler))
    assert provider.generate(_request(), "prompt") == "Dạ được ạ."
    assert "reasoning_effort" not in seen["body"]


def test_provider_error_does_not_include_api_key():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, text="invalid key")

    provider = GeminiProvider("gemini-3.6-flash", "test-key", transport=httpx.MockTransport(handler))
    with pytest.raises(RuntimeError, match="HTTP 401") as error:
        provider.generate(_request(), "prompt")
    assert "test-key" not in str(error.value)
