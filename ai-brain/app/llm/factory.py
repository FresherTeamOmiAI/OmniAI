import os

from app.llm.base import LLMProvider
from app.llm.gemini import GeminiProvider
from app.llm.groq import GroqProvider

# Mặc định khi LLM_MODEL để trống. Khớp decisions #14.
DEFAULT_MODELS = {
    "gemini": "gemini-3.6-flash",
    "groq": "qwen/qwen3.8-27b",
}
DEFAULT_PROVIDER = "gemini"


def get_provider() -> LLMProvider:
    """Chọn provider thật theo LLM_PROVIDER (mặc định gemini, decisions #14 và #25).

    Không còn provider giả. Tên provider lạ hoặc thiếu LLM_API_KEY thì ném ValueError, app không khởi động.
    Model đọc từ LLM_MODEL, key đọc từ LLM_API_KEY, không hard-code. Provider được tạo một lần lúc app khởi động.
    """
    name = os.getenv("LLM_PROVIDER", "").strip().lower() or DEFAULT_PROVIDER
    if name == "fake":
        raise ValueError("LLM_PROVIDER=fake đã bị bỏ (decisions #25). Đặt gemini hoặc groq và điền LLM_API_KEY")
    if name not in DEFAULT_MODELS:
        raise ValueError(f"LLM_PROVIDER '{name}' chưa được hỗ trợ (chọn: {', '.join(DEFAULT_MODELS)})")
    api_key = os.getenv("LLM_API_KEY", "").strip()
    if not api_key:
        raise ValueError(f"LLM_PROVIDER={name} cần LLM_API_KEY")
    model = os.getenv("LLM_MODEL", "").strip() or DEFAULT_MODELS[name]
    if name == "gemini":
        return GeminiProvider(model, api_key)
    return GroqProvider(model, api_key)
