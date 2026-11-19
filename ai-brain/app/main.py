import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.agent.graph import respond
from app.catalog_access import require_config
from app.llm.base import LLMError
from app.llm.factory import get_provider
from app.rag.search import prewarm
from app.schemas import ChatRequest, ChatResponse

log = logging.getLogger(__name__)
# uvicorn chỉ cấu hình logger của nó. Logger "app" in INFO (thời gian từng chặng của /chat, T-43.4) ra stderr.
# Không bật INFO cho root: httpx log mọi URL gọi ra ở mức INFO.
_app_log = logging.getLogger("app")
if not _app_log.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s"))
    _app_log.addHandler(_handler)
    _app_log.setLevel(logging.INFO)

@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Nạp catalog ngầm ngay khi khởi động để câu chat đầu không chờ database (T-43.5). TestClient không chạy lifespan
    # trừ khi dùng `with TestClient(app)`, nên pytest không đụng database.
    prewarm()
    yield


app = FastAPI(title="OmniAI Brain", version="0.1.0", lifespan=lifespan)
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
