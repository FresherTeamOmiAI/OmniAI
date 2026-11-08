# Tổng quan hệ thống

> Cập nhật: 2026-11-08 · commit 1fd584f
> Nguồn: `README.md`, `docker-compose.yml`, `.env.example`, `backend/src/main/resources/application.yml`, `web/vite.config.ts`, `tools/systembat/`

## Mục tiêu
Trợ lý tư vấn mỹ phẩm đa kênh, theo nguyên tắc **"một bộ não, nhiều luật kênh"**. Có 4 trụ (epic): A adapter & luật kênh + bench, B chuyên gia mỹ phẩm, C danh tính & bộ nhớ xuyên kênh, D săn sale. Shop là bản demo: thương hiệu hư cấu, không thanh toán, không giao hàng. Kế hoạch 13 sprint 1 tuần (S0 bắt đầu 03/11/2026), backlog nằm trong `tools/tracking/backlog.py`.

## Kiến trúc & luồng một tin nhắn

```
Kênh (web WS / simulator / sau này Telegram, Shopee, TikTok)
  -> ChannelAdapter<P>.toCanonical()      -> CanonicalMessage
  -> MessageRouter.handle()               idempotency theo channel:messageId (in-memory)
  -> BrainClient.ask()  HTTP POST /chat   ai-brain (FastAPI). Lỗi/timeout 10s -> BrainResponse.unavailable() (handoff=true)
  -> PolicyEngine.apply(channel, reply)   luật trong shared/policies/*.yaml
  -> BotReply {rawText, finalText, violations, blocked, handoff}   trả về kênh
Bench gọi /api/simulator và chấm lại finalText bằng chính bộ YAML đó (Python).
```

## Module & owner

| Thư mục | Công nghệ | Owner | Spec |
|---|---|---|---|
| `backend/` `com.omniai.channel`, `common`, admin inbox | Spring Boot 4.1, Java 21 | TuTM | [backend.md](backend.md) |
| `backend/` `policy`, `bench/`, CI, `tools/` | Java / Python | HungDQ | [policy.md](policy.md), [bench.md](bench.md) |
| `backend/` `catalog`, `customer`, `identity`, `order`, `promotion`, web store | Java | LucVT | [data-model.md](data-model.md) |
| `ai-brain/` | FastAPI, Python 3.11 | ChienPX | [ai-brain.md](ai-brain.md) |
| `web/` | React 19 + Vite 8 + TS, react-router 7 | ai làm phần nào tự làm UI phần đó | [web.md](web.md) |
| `shared/policies/`, `shared/schemas/` | YAML, JSON Schema | HungDQ | [policy.md](policy.md), [api-contracts.md](api-contracts.md) |
| `shared/catalog/`, `tools/catalog/` | CSV 80 SKU + validator + script sinh migration (Python) | LucVT | [data-model.md](data-model.md) |

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
- Backend: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `BRAIN_URL`, `OMNIAI_POLICY_DIR` (mặc định `../shared/policies`, tính từ `backend/`), `WEB_ALLOWED_ORIGINS`, `PORT`.
- AI Brain: `LLM_PROVIDER` (mặc định `fake`), `LLM_MODEL`, `LLM_API_KEY`.
- Kênh (điền khi tới sprint tương ứng): `TELEGRAM_BOT_TOKEN`, `SHOPEE_PARTNER_ID/KEY`, `TIKTOK_APP_KEY/SECRET`.
- Backend và ai-brain **không tự đọc `.env`**: script trong `tools/systembat` nạp hộ, còn IDE thì phải tự cấu hình. Default trong `application.yml` đã khớp `.env.example`.

## Chạy (Windows)
Chi tiết trong `tools/systembat/README.md`:
- Lần đầu: `tools\systembat\setup.bat`
- Chạy tất cả: `run-all.bat`. Tắt: `stop-all.bat`.
- Chạy từng phần: `infra-up.bat`, `run-brain.bat`, `run-backend.bat`, `run-web.bat`.
- Test: `test-all.bat`. Backend test cần Docker (Testcontainers).
- Bench: `run-bench.bat`, cần backend + brain đang chạy.

Lệnh tay (macOS/Linux) xem `README.md` gốc.

## Quy ước chung
- Commit theo Conventional Commits, kèm mã task: `feat(channel): ... [T-17.1]`. Không thêm attribution AI (xem `CLAUDE.md`).
- Mọi con số (giá, tồn kho, khuyến mãi) do AI nói ra phải lấy từ tool gọi API backend, không để LLM tự nói.
- Nhánh (chốt 2026-11-03): `main` (ổn định, chỉ nhận PR) và `dev` (tích hợp). Nhánh tạm `feature/`, `bugfix/`, `docs/`, `chore/` tách từ `dev` rồi PR vào `dev`; `hotfix/` tách từ `main`. Chi tiết: `docs/TEAM_GIT_WORKFLOW.md`.
