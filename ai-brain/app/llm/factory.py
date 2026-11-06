import os

from app.llm.base import LLMProvider
from app.llm.fake import FakeProvider
from app.llm.gemini import GeminiProvider
from app.llm.groq import GroqProvider

# Mặc định khi LLM_MODEL để trống. Khớp decisions #14.
DEFAULT_MODELS = {
    "gemini": "gemini-3.6-flash",
    "groq": "qwen/qwen3.8-27b",
}


def get_provider() -> LLMProvider:
    """Chọn provider theo LLM_PROVIDER (mặc định fake).

    fake không cần key và là mặc định của bench. gemini và groq đọc LLM_MODEL
    cùng LLM_API_KEY, không hard-code key. Provider được tạo một lần lúc app khởi động.
    """
    name = os.getenv("LLM_PROVIDER", "fake").strip().lower()
    if name == "fake":
        return FakeProvider()
    if name not in DEFAULT_MODELS:
        raise ValueError(f"LLM_PROVIDER '{name}' chưa được hỗ trợ")
    api_key = os.getenv("LLM_API_KEY", "").strip()
    if not api_key:
        raise ValueError(f"LLM_PROVIDER={name} cần LLM_API_KEY")
    model = os.getenv("LLM_MODEL", "").strip() or DEFAULT_MODELS[name]
    if name == "gemini":
        return GeminiProvider(model, api_key)
    return GroqProvider(model, api_key)
