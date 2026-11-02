from typing import Protocol

from app.schemas import ChatRequest


class LLMProvider(Protocol):
    """Mọi provider (fake, model thương mại, model local) cùng 1 interface để bench so sánh được."""

    name: str

    def generate(self, request: ChatRequest, system_prompt: str) -> str: ...
