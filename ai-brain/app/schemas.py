"""Hợp đồng API giữa backend (Java) và AI Brain. Khớp với BrainRequest/BrainResponse bên Java."""

from pydantic import BaseModel, Field


class HistoryTurn(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    message_id: str
    channel: str = Field(description="WEB | SHOPEE | TIKTOK")
    channel_owned: bool = Field(description="True nếu là kênh shop sở hữu (được so sánh kênh, gửi link)")
    user_id: str
    customer_id: int | None = None
    text: str
    # Các lượt trước của đúng cuộc chat này, cũ đến mới, không gồm câu hiện tại.
    history: list[HistoryTurn] = Field(default_factory=list)


class ChatResponse(BaseModel):
    reply: str
    provider: str
    warnings: list[str] = []
    handoff: bool = False
