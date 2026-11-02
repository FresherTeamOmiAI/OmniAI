import os

from app.llm.base import LLMProvider
from app.llm.fake import FakeProvider


def get_provider() -> LLMProvider:
    """Chọn provider theo biến môi trường LLM_PROVIDER (mặc định: fake).

    Sprint 1 (ChienPX): thêm provider thật theo kết quả so sánh ở sprint 0,
    đọc LLM_MODEL và LLM_API_KEY từ môi trường, không hard-code key.
    """
    name = os.getenv("LLM_PROVIDER", "fake").lower()
    if name == "fake":
        return FakeProvider()
    raise ValueError(f"LLM_PROVIDER '{name}' chưa được hỗ trợ")
