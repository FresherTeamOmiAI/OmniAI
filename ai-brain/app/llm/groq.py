"""Groq, API tương thích OpenAI. Key và model lấy từ env, không hard-code."""

import httpx

from app.llm.base import LLMError
from app.schemas import ChatRequest

CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
TEMPERATURE = 0.2
MAX_TOKENS = 1024
TIMEOUT_SECONDS = 8.0


class GroqProvider:
    name = "groq"

    def __init__(self, model: str, api_key: str, transport: httpx.BaseTransport | None = None):
        self.model = model
        self._api_key = api_key
        self._transport = transport

    def generate(self, request: ChatRequest, system_prompt: str) -> str:
        payload = {
            "model": self.model,
            "temperature": TEMPERATURE,
            "max_tokens": MAX_TOKENS,
            "tool_choice": "none",
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": request.text},
            ],
        }
        # gpt-oss nghĩ dài và để content rỗng nếu không hạ reasoning_effort.
        if "gpt-oss" in self.model:
            payload["reasoning_effort"] = "low"
        body = self._post(payload)
        message = body.get("choices", [{}])[0].get("message", {})
        answer = (message.get("content") or "").strip()
        if not answer:
            raise LLMError("groq trả nội dung rỗng")
        return answer

    def _post(self, payload: dict) -> dict:
        try:
            with httpx.Client(transport=self._transport, timeout=TIMEOUT_SECONDS) as client:
                response = client.post(
                    CHAT_URL,
                    json=payload,
                    headers={
                        "User-Agent": "omniai-ai-brain/1.0",
                        "Authorization": f"Bearer {self._api_key}",
                    },
                )
        except httpx.HTTPError as error:
            raise LLMError(f"groq không gọi được: {error.__class__.__name__}") from error
        if response.status_code >= 400:
            raise LLMError(f"groq HTTP {response.status_code}: {response.text[:300]}")
        return response.json()
