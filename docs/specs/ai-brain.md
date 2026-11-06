# AI Brain (FastAPI)

> Cập nhật: 2026-11-06 · commit 6eff49a · Owner: ChienPX
> Nguồn: `ai-brain/app/**`, `ai-brain/tests/**`, `ai-brain/requirements.txt`

Python 3.11, FastAPI, Pydantic 2, uvicorn, httpx. Chưa có LangGraph hay SDK LLM nào. Chạy bằng `uvicorn app.main:app --port 8000` (hoặc `run-brain.bat`). Hợp đồng API xem [api-contracts.md](api-contracts.md).

## Cấu trúc

| File | Vai trò |
|---|---|
| `app/main.py` | `GET /health`, `POST /chat`. Provider được tạo **một lần** lúc khởi động |
| `app/schemas.py` | `ChatRequest`, `ChatResponse`, phải khớp `BrainRequest/Response` bên Java |
| `app/llm/base.py` | `LLMProvider` (Protocol): `name`, `generate(request, system_prompt) -> str` |
| `app/llm/factory.py` | `get_provider()` đọc `LLM_PROVIDER`: `fake` (mặc định), `gemini`, `groq`. Model thật cần `LLM_API_KEY`; `LLM_MODEL` trống thì lấy default của decisions #14 |
| `app/llm/fake.py` | `FakeProvider`: trả lời cố định và **cố tình vi phạm** (link, Zalo/SĐT, "đặc trị") để policy có cái mà chặn. Hỏi giá (`giá`, `bao nhiêu`, `còn hàng`, `tồn kho`) thì cố tình bịa "450.000đ, còn 15 sản phẩm, giảm 10%" để thử `grounding.py`. Bench offline dựa vào hành vi này |
| `app/agent/graph.py` | `respond()`: phát hiện mang thai bằng từ khóa → `check_routine` → gọi provider → `strip_unverified_numbers` → thêm "Lưu ý: ..." nếu có cảnh báo. `system_prompt_for()` khác nhau giữa kênh owned và kênh sàn |
| `app/rules/ingredients.py` | Luật thành phần **tất định**: nhóm hoạt chất (retinoid, aha, bha, bpo, vitamin_c), cặp xung đột, nhóm cần tránh khi mang thai. Mã cảnh báo: `conflict:<a>+<b>`, `pregnancy:<group>` |
| `app/rules/grounding.py` | T-06.6, **tất định**: bỏ câu có giá (`450.000đ`, `289k`...), tồn kho (`tồn 15`, `còn 3 chai`), khuyến mãi (`giảm 10%`) do LLM tự nêu, rồi thêm câu hẹn kiểm tra. Nồng độ như "BHA 2%" không bị bắt. Mã cảnh báo: `grounding:price`, `grounding:stock`, `grounding:promo` |

## Nguyên tắc
- LLM chỉ diễn giải. Luật an toàn (xung đột, mang thai, dị ứng) phải là code tất định có pytest.
- Con số (giá, tồn kho, khuyến mãi) lấy qua tool gọi API backend, không để LLM tự sinh. Hiện chưa có tool nên `grounding.py` bỏ mọi số loại này trong câu LLM. Khi có tool (US-15), chỉ cho qua số khớp kết quả tool của lượt đó.
- Provider thật phải tự tuân thủ luật kênh. Policy bên backend chỉ là lưới an toàn.
- Không hard-code API key, đọc `LLM_MODEL`, `LLM_API_KEY` từ env.
- Chỉ dùng provider **miễn phí** (decisions #13): free tier hoặc model mở chạy local, chỉ gửi dữ liệu tổng hợp. Model được chọn theo tiêu chí trong `docs/llm-eval/README.md`.
- Model đã chọn (tạm, decisions #14): chính `gemini` / `gemini-3.6-flash`, dự phòng `groq` / `qwen/qwen3.8-27b`. Factory đã chọn được hai provider này qua env (T-06.3). T-06.4 mới chạy 10 câu mẫu và đo p95.
- Đổi hành vi `FakeProvider` sẽ làm hỏng `bench/datasets/sample.jsonl`, nên phải sửa dataset cùng lúc.

## Test
- `tests/test_api.py`: health, chat có trả lời, mang thai + retinol thì có cảnh báo, hỏi giá thì không còn số tự bịa.
- `tests/test_provider.py`: `LLM_PROVIDER` đổi giữa fake, gemini và groq; thiếu key thì lỗi; HTTP được giả lập, không gọi API thật.
- `tests/test_grounding.py`: các dạng số giá/tồn/khuyến mãi bị bỏ; nồng độ %, SPF, dung tích, "3 đến 5 đêm" không bị bắt nhầm; dòng khác giữ nguyên định dạng.
- `tests/test_ingredients.py`: nhận nhóm từ INCI, retinoid + BHA xung đột, routine dịu nhẹ không cảnh báo, cảnh báo mang thai chỉ khi đang mang thai.

## Lộ trình (backlog)
- S1 (US-06): T-06.4 tích hợp `gemini-3.6-flash` (decisions #14). Phải tách token suy nghĩ khỏi `max_tokens` và đo lại p95 trước Sprint Review S1 (16/11).
- S2 (US-10): embedding catalog vào pgvector + tool `search_product`.
- S3 (US-15): agent loop có tool (LangGraph) + `get_price_and_stock`.
- S4–S8: `get_customer_profile`, `get_orders_all_channels`, `check_ingredients`, routine builder, `get_promotions`, handoff.
- `ingredients.py` hiện là **bản mẫu**, cần người có chuyên môn review.
