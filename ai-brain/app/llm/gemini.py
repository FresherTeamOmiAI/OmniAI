"""Gemini qua AI Studio. Key và model lấy từ env, không hard-code.

thinkingLevel=low để token suy nghĩ không nuốt hết câu trả lời. T-06.4 đo lại p95
và tách ngân sách token suy nghĩ khỏi max_tokens trước Sprint Review S1.
"""

import httpx

from app.schemas import ChatRequest

GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
TEMPERATURE = 0.2
MAX_OUTPUT_TOKENS = 1024
TIMEOUT_SECONDS = 8.0


class GeminiProvider:
    name = "gemini"

    def __init__(self, model: str, api_key: str, transport: httpx.BaseTransport | None = None):
        self.model = model
        self._api_key = api_key
        self._transport = transport

    def generate(self, request: ChatRequest, system_prompt: str) -> str:
        payload = {
            "systemInstruction": {"parts": [{"text": system_prompt}]},
            "contents": [{"role": "user", "parts": [{"text": request.text}]}],
            "generationConfig": {
                "temperature": TEMPERATURE,
                "maxOutputTokens": MAX_OUTPUT_TOKENS,
                "thinkingConfig": {"thinkingLevel": "low"},
            },
        }
        url = GENERATE_URL.format(model=self.model)
        body = self._post(url, payload, {"x-goog-api-key": self._api_key})
        parts = body.get("candidates", [{}])[0].get("content", {}).get("parts", [])
        answer = "".join(part.get("text", "") for part in parts if not part.get("thought")).strip()
        if not answer:
            raise RuntimeError("gemini trả nội dung rỗng")
        return answer

    def _post(self, url: str, payload: dict, headers: dict) -> dict:
        with httpx.Client(transport=self._transport, timeout=TIMEOUT_SECONDS) as client:
            response = client.post(
                url,
                json=payload,
                headers={"User-Agent": "omniai-ai-brain/1.0", **headers},
            )
        if response.status_code >= 400:
            raise RuntimeError(f"gemini HTTP {response.status_code}: {response.text[:300]}")
        return response.json()
