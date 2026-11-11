import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.agent.graph import respond
from app.catalog_access import require_config
from app.llm.base import LLMError
from app.llm.factory import get_provider
from app.schemas import ChatRequest, ChatResponse

log = logging.getLogger(__name__)

app = FastAPI(title="OmniAI Brain", version="0.1.0")
# Thiếu CHATBOT_DB_URL hoặc LLM_API_KEY thì dừng ngay lúc khởi động, không chạy ở chế độ giả (decisions #25).
require_config()
provider = get_provider()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "provider": provider.name}


@app.exception_handler(LLMError)
async def llm_unavailable(request: Request, error: LLMError) -> JSONResponse:
    log.warning("LLM không trả lời được: %s", error)
    return JSONResponse(status_code=502, content={"error": "llm_unavailable"})


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    return respond(request, provider)
