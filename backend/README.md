# backend

Spring Boot 4.1 · Java 21 · Maven. Modular monolith, mỗi package có 1 owner:

| Package | Owner | Nội dung |
|---|---|---|
| `common` | chung | `CanonicalMessage`, `ChannelType`, `BotReply` |
| `channel` | TuTM | `ChannelAdapter`, `MessageRouter`, `web/` (WebSocket `/ws/chat`), `simulator/` (`POST /api/simulator/{channel}/messages`) |
| `policy` | HungDQ | `PolicyEngine` đọc `../shared/policies/*.yaml` |
| `brain` | TuTM + ChienPX | `HttpBrainClient` gọi ai-brain; brain lỗi thì trả câu chờ + `handoff=true` |
| `catalog`, `customer`, `identity`, `promotion`, `order` | LucVT | Entity, repository, `GET /api/products` |

```bash
mvn spring-boot:run      # cần Postgres: docker compose up -d (ở thư mục gốc)
mvn test                 # cần Docker đang chạy (Testcontainers tự bật Postgres + pgvector)
```

Migration ở `src/main/resources/db/migration` (Flyway, PostgreSQL 16 + pgvector). Không sửa migration đã chạy, luôn thêm file `V<n>__mô_tả.sql` mới.
