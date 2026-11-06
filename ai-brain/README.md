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
| `app/llm/` | `LLMProvider` + `FakeProvider` (mặc định, không cần key). Chọn bằng `LLM_PROVIDER` |
| `app/agent/graph.py` | Điều phối 1 lượt: system prompt theo kênh, luật thành phần, gọi provider. Sprint 1–3 thay bằng agent có tool |
| `app/rules/ingredients.py` | Luật xung đột hoạt chất tất định. **Bản mẫu, cần người có chuyên môn review** |
| `app/rules/grounding.py` | Bỏ câu có giá, tồn kho, khuyến mãi do LLM tự nêu (T-06.6). Tất định, có pytest |

`FakeProvider` cố tình trả lời "ngây thơ" (gợi ý web, đưa SĐT, dùng chữ "đặc trị") để kiểm tra policy engine của backend. Hỏi giá thì nó cố tình bịa số để kiểm tra `grounding.py`.
