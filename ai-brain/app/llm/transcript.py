"""Ghép lịch sử cuộc chat vào request LLM. Câu hiện tại chỉ thêm một lần."""

from app.schemas import ChatRequest


def groq_messages(request: ChatRequest, system_prompt: str) -> list[dict[str, str]]:
    messages = [{"role": "system", "content": system_prompt}]
    for role, content in _turns(request, assistant_role="assistant"):
        messages.append({"role": role, "content": content})
    return messages


def gemini_contents(request: ChatRequest) -> list[dict]:
    contents = []
    for role, content in _turns(request, assistant_role="model"):
        contents.append({"role": role, "parts": [{"text": content}]})
    return contents


def _turns(request: ChatRequest, assistant_role: str) -> list[tuple[str, str]]:
    turns = []
    for turn in request.history:
        role = assistant_role if turn.role.lower() == "assistant" else "user"
        turns.append((role, turn.content))
    turns.append(("user", request.text))
    return turns
