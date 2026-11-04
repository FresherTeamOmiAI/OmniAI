# AI Brain (FastAPI)

> Cập nhật: 2026-11-04 · commit b177a3b · Owner: ChienPX
> Nguồn: `ai-brain/app/**`, `ai-brain/tests/**`, `ai-brain/requirements.txt`

Python 3.11, FastAPI, Pydantic 2, uvicorn, httpx. Chưa có LangGraph hay SDK LLM nào. Chạy bằng `uvicorn app.main:app --port 8000` (hoặc `run-brain.bat`). Hợp đồng API xem [api-contracts.md](api-contracts.md).

## Cấu trúc

| File | Vai trò |
|---|---|
| `app/main.py` | `GET /health`, `POST /chat`. Provider được tạo **một lần** lúc khởi động |
| `app/schemas.py` | `ChatRequest`, `ChatResponse`, phải khớp `BrainRequest/Response` bên Java |
| `app/llm/base.py` | `LLMProvider` (Protocol): `name`, `generate(request, system_prompt) -> str` |
| `app/llm/factory.py` | `get_provider()` đọc `LLM_PROVIDER`. Hiện chỉ có `fake`, giá trị khác thì `ValueError` |
| `app/llm/fake.py` | `FakeProvider`: trả lời cố định và **cố tình vi phạm** (link, Zalo/SĐT, "đặc trị") để policy có cái mà chặn. Bench offline dựa vào hành vi này |
| `app/agent/graph.py` | `respond()`: phát hiện mang thai bằng từ khóa → `check_routine` → gọi provider → thêm "Lưu ý: ..." nếu có cảnh báo. `system_prompt_for()` khác nhau giữa kênh owned và kênh sàn |
| `app/rules/ingredients.py` | Luật thành phần **tất định**: nhóm hoạt chất (retinoid, aha, bha, bpo, vitamin_c), cặp xung đột, nhóm cần tránh khi mang thai. Mã cảnh báo: `conflict:<a>+<b>`, `pregnancy:<group>` |

## Nguyên tắc
- LLM chỉ diễn giải. Luật an toàn (xung đột, mang thai, dị ứng) phải là code tất định có pytest.
- Con số (giá, tồn kho, khuyến mãi) lấy qua tool gọi API backend, không để LLM tự sinh.
- Provider thật phải tự tuân thủ luật kênh. Policy bên backend chỉ là lưới an toàn.
- Không hard-code API key, đọc `LLM_MODEL`, `LLM_API_KEY` từ env.
- Chỉ dùng provider **miễn phí** (decisions #13): free tier hoặc model mở chạy local, chỉ gửi dữ liệu tổng hợp. Model được chọn theo tiêu chí trong `docs/llm-eval/README.md`.
- Đổi hành vi `FakeProvider` sẽ làm hỏng `bench/datasets/sample.jsonl`, nên phải sửa dataset cùng lúc.

## Test
- `tests/test_api.py`: health, chat có trả lời, mang thai + retinol thì có cảnh báo.
- `tests/test_ingredients.py`: nhận nhóm từ INCI, retinoid + BHA xung đột, routine dịu nhẹ không cảnh báo, cảnh báo mang thai chỉ khi đang mang thai.

## Lộ trình (backlog)
- S1 (US-06): T-06.4 tích hợp provider thật theo kết quả so sánh model ở S0 (US-05).
- S2 (US-10): embedding catalog vào pgvector + tool `search_product`.
- S3 (US-15): agent loop có tool (LangGraph) + `get_price_and_stock`.
- S4–S8: `get_customer_profile`, `get_orders_all_channels`, `check_ingredients`, routine builder, `get_promotions`, handoff.
- `ingredients.py` hiện là **bản mẫu**, cần người có chuyên môn review.
