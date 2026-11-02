from fastapi import FastAPI

from app.agent.graph import respond
from app.llm.factory import get_provider
from app.schemas import ChatRequest, ChatResponse

app = FastAPI(title="OmniAI Brain", version="0.1.0")
provider = get_provider()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "provider": provider.name}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    return respond(request, provider)
