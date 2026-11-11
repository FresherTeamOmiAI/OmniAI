# AI Brain (FastAPI)

> Cập nhật: 2026-11-11 · commit 38afec6 · Owner: ChienPX
> Nguồn: `ai-brain/app/**`, `ai-brain/tests/**`, `ai-brain/requirements.txt`

Python 3.11, FastAPI, Pydantic 2, uvicorn, httpx, psycopg 3, sqlglot. Chưa có LangGraph hay SDK LLM nào. Chạy bằng `uvicorn app.main:app --port 8000` (hoặc `run-brain.bat`). Hợp đồng API xem [api-contracts.md](api-contracts.md).

## Cấu trúc

| File | Vai trò |
|---|---|
| `app/main.py` | `GET /health`, `POST /chat`. Provider được tạo **một lần** lúc khởi động. Thiếu `CHATBOT_DB_URL` hoặc `LLM_API_KEY` thì **không khởi động** (decisions #25) |
| `app/schemas.py` | `ChatRequest`, `ChatResponse`, phải khớp `BrainRequest/Response` bên Java |
| `app/llm/base.py` | `LLMProvider` (Protocol): `name`, `generate(request, system_prompt) -> str`. `LLMError` khi provider lỗi |
| `app/llm/factory.py` | `get_provider()` đọc `LLM_PROVIDER`: `gemini` (mặc định, kể cả khi để trống) hoặc `groq`. Bắt buộc có `LLM_API_KEY`; `LLM_MODEL` trống thì lấy default của decisions #14. **Không còn `fake`**: đặt `LLM_PROVIDER=fake` thì báo lỗi rõ |
| `app/catalog_access.py` | `require_config()` gọi lúc khởi động. `load_public_catalog(channel, owned)` chạy 1 câu SELECT cố định trên view `chatbot_catalog` bằng `CHATBOT_DB_URL` (role `chatbot_reader`, transaction chỉ đọc, timeout 2 s), trả `CatalogSnapshot(lines, prices)`. Kênh owned thấy giá 3 kênh, kênh sàn chỉ thấy giá của sàn đó. Lỗi kết nối lúc đang chạy thì trả snapshot rỗng, chat vẫn chạy |
| `app/rules/sql_guard.py` | `validate_sql()` bằng SQLGlot: chỉ 1 câu SELECT, chỉ view `chatbot_catalog`, chỉ cột công khai, không JOIN/UNION, LIMIT ≤ 100. Quy tắc đầy đủ: `app/rules/chatbot_database_security.md` |
| `app/llm/fake.py` | `FakeProvider`: trả lời cố định và **cố tình vi phạm** (link, Zalo/SĐT, "đặc trị") để policy có cái mà chặn. Hỏi giá (`giá`, `bao nhiêu`, `còn hàng`, `tồn kho`) thì cố tình bịa "450.000đ, còn 15 sản phẩm, giảm 10%" để thử `grounding.py`. Bench offline dựa vào hành vi này |
| `app/agent/graph.py` | `respond()`: phát hiện mang thai bằng từ khóa → `check_routine` → `load_public_catalog` → gọi provider với catalog trong system prompt → `strip_unverified_numbers` → thêm "Lưu ý: ..." nếu có cảnh báo. `system_prompt_for()` khác nhau giữa kênh owned và kênh sàn, và yêu cầu văn bản thuần, không markdown, tối đa khoảng 5 câu |
| `app/rules/ingredients.py` | Luật thành phần **tất định**: nhóm hoạt chất (retinoid, aha, bha, bpo, vitamin_c), cặp xung đột, nhóm cần tránh khi mang thai. Mã cảnh báo: `conflict:<a>+<b>`, `pregnancy:<group>` |
| `app/rules/grounding.py` | T-06.6, **tất định**: bỏ câu có tồn kho (`tồn 15`, `còn 3 chai`), khuyến mãi (`giảm 10%`) và giá (`450.000đ`, `289k`...) do LLM tự nêu, rồi thêm câu hẹn kiểm tra. Giá được giữ khi mọi số tiền trong câu khớp giá vừa đọc từ `chatbot_catalog`. Nồng độ như "BHA 2%" không bị bắt. Mã cảnh báo: `grounding:price`, `grounding:stock`, `grounding:promo` |

## Nguyên tắc
- LLM chỉ diễn giải. Luật an toàn (xung đột, mang thai, dị ứng) phải là code tất định có pytest.
- Con số (giá, tồn kho, khuyến mãi) không để LLM tự sinh. Giá niêm yết đọc từ view `chatbot_catalog` và chỉ được giữ khi khớp. Tồn kho và khuyến mãi chưa có nguồn nên `grounding.py` bỏ hết; khi có tool (US-15), chỉ cho qua số khớp kết quả tool của lượt đó.
- Provider thật phải tự tuân thủ luật kênh. Policy bên backend chỉ là lưới an toàn.
- Không hard-code API key, đọc `LLM_MODEL`, `LLM_API_KEY` từ env.
- Chỉ dùng provider **miễn phí** (decisions #13): free tier hoặc model mở chạy local, chỉ gửi dữ liệu tổng hợp. Model được chọn theo tiêu chí trong `docs/llm-eval/README.md`.
- Model đã chọn (tạm, decisions #14): chính `gemini` / `gemini-3.6-flash`, dự phòng `groq` / `qwen/qwen3.8-27b`. Factory đã chọn được hai provider này qua env (T-06.3). T-06.4 mới chạy 10 câu mẫu và đo p95.
- App chỉ chạy LLM thật và database thật (decisions #25). Bench đo trên Gemini + Postgres, nên kỳ vọng trong dataset không được phụ thuộc câu chữ của một model.
- Lỗi provider (T-06.7): Gemini và Groq ném `LLMError` khi HTTP ≥ 400, nội dung rỗng, hoặc `httpx.HTTPError` (timeout, mất mạng). `POST /chat` bắt lỗi này, ghi log WARNING và trả **502** `{"error": "llm_unavailable"}`. Backend coi 502 là không gọi được brain rồi trả `BrainResponse.unavailable()` để chuyển nhân viên.

## Test
- `tests/conftest.py` + `tests/stubs.py`: đặt env giả trước khi import app, rồi thay provider bằng `StubProvider` và thay `load_public_catalog` bằng một snapshot cố định cho mọi test. Stub **chỉ nằm trong `tests/`**, không chọn được qua env. pytest không gọi mạng, không cần database.
- `tests/test_api.py`: health, chat có trả lời, mang thai + retinol thì có cảnh báo, hỏi giá thì bỏ số tự bịa, giá khớp catalog thì được giữ.
- `tests/test_provider.py`: mặc định là gemini; `fake` bị từ chối; thiếu key hoặc thiếu `CHATBOT_DB_URL` thì lỗi; đổi được sang groq; HTTP được giả lập, không gọi API thật. Gemini HTTP 500 và `httpx.ConnectError` ném `LLMError`. `POST /chat` khi provider ném `LLMError` thì trả 502.
- `tests/test_sql_guard.py`: câu đụng bảng `customer`, JOIN, UNION, nhiều câu lệnh, cột lạ đều bị chặn.
- `tests/test_grounding.py`: các dạng số giá/tồn/khuyến mãi bị bỏ; nồng độ %, SPF, dung tích, "3 đến 5 đêm" không bị bắt nhầm; dòng khác giữ nguyên định dạng.
- `tests/test_ingredients.py`: nhận nhóm từ INCI, retinoid + BHA xung đột, routine dịu nhẹ không cảnh báo, cảnh báo mang thai chỉ khi đang mang thai.

## Lộ trình (backlog)
- S1 (US-06): T-06.4 đã chạy 10/10 câu mẫu tiếng Việt qua `gemini-3.6-flash` (`ai-brain/samples/vi-context-results.jsonl`, 2026-11-06). API vẫn cộng token suy nghĩ vào `maxOutputTokens`, nên trần để 2048 và `thinkingLevel=low`. p95 của 10 câu là 6.716 ms, 4 câu vượt 5 s. PO chọn nới NFR-01 hoặc đổi sang qwen trước Sprint Review S1 (16/11).
- S2 (US-10): embedding catalog vào pgvector + tool `search_product`.
- S3 (US-15): agent loop có tool (LangGraph) + `get_price_and_stock`.
- S4–S8: `get_customer_profile`, `get_orders_all_channels`, `check_ingredients`, routine builder, `get_promotions`, handoff.
- `ingredients.py` hiện là **bản mẫu**, cần người có chuyên môn review.
