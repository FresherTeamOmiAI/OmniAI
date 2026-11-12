# Tổng quan hệ thống

> Cập nhật: 2026-11-12 · commit 1324b0f
> Nguồn: `README.md`, `docker-compose.yml`, `.env.example`, `backend/src/main/resources/application.yml`, `web/vite.config.ts`, `tools/systembat/`

## Mục tiêu
Hub omnichannel cho shop mỹ phẩm kèm trợ lý tư vấn, theo nguyên tắc **"một bộ não, nhiều luật kênh"**. Hub giữ dữ liệu gốc, đẩy ra các kênh và gom dữ liệu từ các kênh về (decisions #23). Có 5 trụ (epic): A simulator kênh sàn & luật kênh + bench, B chuyên gia mỹ phẩm, C danh tính & bộ nhớ xuyên kênh, D săn sale, E đồng bộ dữ liệu hub ↔ kênh. Kênh: WEB, SHOPEE, TIKTOK; hai sàn chạy 100% bằng simulator theo schema thật (decisions #22). Shop là bản demo: không thanh toán, không giao hàng. Kế hoạch 13 sprint (S0 bắt đầu 03/11/2026, từ S2 mỗi sprint kéo lên 1 tuần, S12 kết thúc 25/01/2027, sau đó 1 tuần đệm; decisions #24), backlog nằm trong `tools/tracking/backlog.py`.

## Kiến trúc & luồng một tin nhắn

```
Kênh (web WS / simulator chung / từ S2: Shopee, TikTok Shop qua simulator theo schema thật)
  -> ChannelAdapter<P>.toCanonical()      -> CanonicalMessage
  -> MessageRouter.handle()               idempotency theo channel:messageId (in-memory)
  -> BrainClient.ask()  HTTP POST /chat   ai-brain (FastAPI). Lỗi/timeout 10s -> BrainResponse.unavailable() (handoff=true)
  -> PolicyEngine.apply(channel, reply)   luật trong shared/policies/*.yaml
  -> BotReply {rawText, finalText, violations, blocked, handoff}   trả về kênh
Bench gọi /api/simulator và chấm lại finalText bằng chính bộ YAML đó (Python).

Dữ liệu 2 chiều hub <-> sàn (trụ E, chưa có code, bảng US ở backend.md):
  ĐI  hub -> sàn : sản phẩm, giá theo kênh, tồn kho, khuyến mãi, trạng thái xử lý đơn
  VỀ  sàn -> hub : hội thoại CSKH, đơn hàng, trạng thái duyệt/vi phạm sản phẩm, đánh giá
```

## Module & owner

| Thư mục | Công nghệ | Owner | Spec |
|---|---|---|---|
| `backend/` `com.omniai.channel`, `common`, admin inbox, đồng bộ hub ↔ kênh, simulator TikTok Shop | Spring Boot 4.1, Java 21 | TuTM | [backend.md](backend.md) |
| `backend/` `policy`, `bench/`, CI, `tools/` | Java / Python | HungDQ | [policy.md](policy.md), [bench.md](bench.md) |
| `backend/` `catalog`, `customer`, `identity`, `order`, `promotion`, web store | Java | LucVT | [data-model.md](data-model.md) |
| `ai-brain/`; simulator Shopee (trong `backend/`) | FastAPI, Python 3.11; Java | ChienPX | [ai-brain.md](ai-brain.md), [backend.md](backend.md) |
| `web/` | React 19 + Vite 8 + TS, react-router 7 | ai làm phần nào tự làm UI phần đó | [web.md](web.md) |
| `shared/policies/`, `shared/schemas/` | YAML, JSON Schema | HungDQ | [policy.md](policy.md), [api-contracts.md](api-contracts.md) |
| `shared/catalog/`, `tools/catalog/` | Catalog thật 366 SKU (`products.csv`, sinh từ `real/selection.csv`) + `obf_fetch.py`/`obf_annotate.py` lấy dữ liệu Open Beauty Facts + validator + script sinh migration + `upload_images.py` đẩy ảnh lên Supabase (Python; `obf_fetch.py build` cần Pillow, `tools/catalog/requirements.txt`) | LucVT | [data-model.md](data-model.md), [../catalog/du-lieu-san-pham-that.md](../catalog/du-lieu-san-pham-that.md) |

## Cổng

| Dịch vụ | Cổng | Kiểm tra |
|---|---|---|
| Web (Vite) | 5173 | http://localhost:5173, trang thử luật `/admin/policy` |
| Backend | 8080 (`PORT`) | `/actuator/health` |
| AI Brain | 8000 | `/health` |
| Postgres 16 + pgvector | 5432 | user/pass/db `omniai` |
| Redis | 6379 | (chưa dùng, dự kiến S7) |
| RabbitMQ | 5672, UI 15672 | (chưa dùng, dự kiến S7) |

## Biến môi trường (`.env.example`)
- Docker: `POSTGRES_DB/USER/PASSWORD/PORT`, `REDIS_PORT`, `RABBITMQ_PORT`, `RABBITMQ_UI_PORT`.
- Backend: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `BRAIN_URL`, `OMNIAI_POLICY_DIR` (mặc định `../shared/policies`, tính từ `backend/`), `WEB_ALLOWED_ORIGINS`, `PORT`, `ADMIN_USERNAME`/`ADMIN_PASSWORD` (tài khoản `/api/admin/**`, mặc định `admin`/`admin`, chỉ dùng khi dev), `SUPABASE_URL` + `SUPABASE_BUCKET` (mặc định `product-images`) để ghép URL ảnh sản phẩm (trống thì `imageUrl = null`).
- Ảnh sản phẩm: `SUPABASE_SERVICE_KEY` (khoá service_role) **chỉ** dùng cho `tools/catalog/upload_images.py`, không commit, không đưa vào backend/web. Cách tạo project: `docs/catalog/du-lieu-san-pham-that.md`.
- AI Brain: `LLM_PROVIDER` (`gemini` mặc định, hoặc `groq`; không còn `fake`), `LLM_MODEL`, `LLM_API_KEY` (**bắt buộc**), `CHATBOT_DB_URL` (**bắt buộc**, role `chatbot_reader` chỉ đọc view `chatbot_catalog`). Thiếu một trong hai biến bắt buộc thì brain không khởi động (decisions #25).
- Backend còn đọc `CHATBOT_DB_PASSWORD` để bật đăng nhập cho role `chatbot_reader` lúc khởi động.
- Kênh sàn (khóa test để simulator ký webhook và adapter kiểm chữ ký, US-21, US-25): `SHOPEE_PARTNER_ID/KEY`, `TIKTOK_APP_KEY/SECRET`.
- Backend và ai-brain **không tự đọc `.env`**: script trong `tools/systembat` nạp hộ, còn IDE thì phải tự cấu hình. Default trong `application.yml` đã khớp `.env.example`.

## Chạy (Windows)
Chi tiết trong `tools/systembat/README.md`:
- Lần đầu: `tools\systembat\setup.bat`
- Chạy tất cả: `run-all.bat`. Tắt: `stop-all.bat`.
- Chạy từng phần: `infra-up.bat`, `run-brain.bat`, `run-backend.bat`, `run-web.bat`.
- Test: `test-all.bat`. Backend test cần Docker (Testcontainers).
- Bench: `run-bench.bat`, cần backend + brain đang chạy, tức là cần `LLM_API_KEY` và tốn quota LLM thật.

Lệnh tay (macOS/Linux) xem `README.md` gốc.

## Quy ước chung
- Commit theo Conventional Commits, kèm mã task: `feat(channel): ... [T-17.1]`. Không thêm attribution AI (xem `CLAUDE.md`).
- Mọi con số (giá, tồn kho, khuyến mãi) do AI nói ra phải lấy từ tool gọi API backend, không để LLM tự nói.
- Nhánh (chốt 2026-11-03): `main` (ổn định, chỉ nhận PR) và `dev` (tích hợp). Nhánh tạm `feature/`, `bugfix/`, `docs/`, `chore/` tách từ `dev` rồi PR vào `dev`; `hotfix/` tách từ `main`. Chi tiết: `docs/TEAM_GIT_WORKFLOW.md`.
