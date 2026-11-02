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
mvn spring-boot:run                                  # Postgres (docker compose up -d)
mvn spring-boot:run -Dspring-boot.run.profiles=h2    # không cần Docker
mvn test
```

Migration ở `src/main/resources/db/migration` (Flyway). Viết SQL sao cho chạy được trên cả PostgreSQL và H2. Riêng phần dùng pgvector thì để ở migration riêng và chỉ chạy trên Postgres.
