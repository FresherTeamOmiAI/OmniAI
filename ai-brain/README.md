# ai-brain (Owner: ChienPX)

FastAPI service. Backend gọi `POST /chat` cho mỗi tin nhắn.

```bash
python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000     # http://localhost:8000/docs
python -m pytest
```

| File | Nội dung |
|---|---|
| `app/main.py` | `/health`, `/chat` |
| `app/schemas.py` | `ChatRequest` / `ChatResponse` (khớp `BrainRequest` / `BrainResponse` bên Java) |
| `app/llm/` | `LLMProvider`. `LLM_PROVIDER=gemini` (mặc định) hoặc `groq`. Đọc `LLM_MODEL` và `LLM_API_KEY` (bắt buộc) |
| `app/catalog_access.py` | Đọc view `chatbot_catalog` bằng `CHATBOT_DB_URL` (bắt buộc), role chỉ đọc `chatbot_reader` |
| `app/agent/graph.py` | Điều phối 1 lượt: system prompt theo kênh, luật thành phần, gọi provider. Sprint 1–3 thay bằng agent có tool |
| `app/rules/ingredients.py` | Luật xung đột hoạt chất tất định. **Bản mẫu, cần người có chuyên môn review** |
| `app/rules/grounding.py` | Bỏ câu có tồn kho, khuyến mãi và giá do LLM tự nêu; giữ giá khớp catalog (T-06.6). Tất định, có pytest |

Brain chỉ chạy LLM thật và database thật: thiếu `LLM_API_KEY` hoặc `CHATBOT_DB_URL` thì `uvicorn` dừng ngay với lỗi nói rõ biến nào thiếu. Không còn `LLM_PROVIDER=fake`.

`python -m pytest` không cần key hay database: `tests/conftest.py` thay provider bằng `StubProvider` và thay catalog bằng một snapshot cố định (`tests/stubs.py`). Stub cố tình bịa giá, tồn kho, khuyến mãi khi được hỏi giá để kiểm tra `grounding.py`.
