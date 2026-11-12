"""Gemini qua AI Studio. Key và model lấy từ env, không hard-code.

API cộng token suy nghĩ vào maxOutputTokens. Gemini 3 không nhận thinkingBudget
cùng thinkingLevel. Mức low giữ phần suy nghĩ nhỏ; trần 2048 chừa chỗ cho câu
trả lời nên câu không bị cắt như lúc trần 400 (T-06.4).
"""

import httpx

from app.llm.base import LLMError
from app.llm.transcript import gemini_contents
from app.schemas import ChatRequest

GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
TEMPERATURE = 0.2
MAX_OUTPUT_TOKENS = 2048
TIMEOUT_SECONDS = 9.0


class GeminiProvider:
    name = "gemini"

    def __init__(self, model: str, api_key: str, transport: httpx.BaseTransport | None = None):
        self.model = model
        self._api_key = api_key
        self._transport = transport
        self.last_finish_reason = ""
        self.last_usage: dict = {}

    def generate(self, request: ChatRequest, system_prompt: str) -> str:
        payload = {
            "systemInstruction": {"parts": [{"text": system_prompt}]},
            "contents": gemini_contents(request),
            "generationConfig": {
                "temperature": TEMPERATURE,
                "maxOutputTokens": MAX_OUTPUT_TOKENS,
                "thinkingConfig": {"thinkingLevel": "low"},
            },
        }
        url = GENERATE_URL.format(model=self.model)
        body = self._post(url, payload, {"x-goog-api-key": self._api_key})
        self.last_usage = body.get("usageMetadata") or {}
        candidate = (body.get("candidates") or [{}])[0]
        self.last_finish_reason = candidate.get("finishReason") or ""
        parts = candidate.get("content", {}).get("parts", [])
        answer = "".join(part.get("text", "") for part in parts if not part.get("thought")).strip()
        if not answer:
            raise LLMError("gemini trả nội dung rỗng")
        return answer

    def _post(self, url: str, payload: dict, headers: dict) -> dict:
        try:
            with httpx.Client(transport=self._transport, timeout=TIMEOUT_SECONDS) as client:
                response = client.post(
                    url,
                    json=payload,
                    headers={"User-Agent": "omniai-ai-brain/1.0", **headers},
                )
        except httpx.HTTPError as error:
            raise LLMError(f"gemini không gọi được: {error.__class__.__name__}") from error
        if response.status_code >= 400:
            raise LLMError(f"gemini HTTP {response.status_code}: {response.text[:800]}")
        return response.json()
