from typing import Protocol

from app.schemas import ChatRequest


class LLMProvider(Protocol):
    """Mọi provider (API free tier, model local) cùng 1 interface để bench so sánh được.

    App chỉ có provider thật. pytest dùng stub trong tests/stubs.py (decisions #25).
    """

    name: str

    def generate(self, request: ChatRequest, system_prompt: str) -> str: ...
