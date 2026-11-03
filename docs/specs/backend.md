# Backend (Spring Boot)

> Cập nhật: 2026-11-03 · commit c88bfc6
> Nguồn: `backend/pom.xml`, `backend/src/main/java/com/omniai/**`, `backend/src/test/**`

Spring Boot 4.1.2, Java 21, Maven (chưa có `mvnw`). Starter đang dùng: webmvc, websocket, validation, data-jpa, flyway (+postgresql), actuator, lombok. Test dùng spring-boot-starter-test + Testcontainers (postgresql). Kiến trúc modular monolith: mỗi package là một module.

## Package `com.omniai.*`

| Package | Trách nhiệm | File chính | Owner |
|---|---|---|---|
| `common` | Kiểu dùng chung giữa các module | `CanonicalMessage`, `BotReply`, `ChannelType` | TuTM |
| `channel` | Luồng chung + adapter từng kênh | `ChannelAdapter<P>`, `MessageRouter` | TuTM |
| `channel.web` | Kênh WEB qua WebSocket | `WebChatAdapter` (là TextWebSocketHandler), `WebSocketConfig`, `WebChatInbound` | TuTM |
| `channel.simulator` | Giả lập kênh bất kỳ (bench, khi chưa có API sàn) | `SimulatorController`, `SimulatorAdapter` (record, không phải bean), `SimulatorRequest` | TuTM |
| `brain` | Client gọi ai-brain | `BrainClient` (interface để mock), `HttpBrainClient` (RestClient), `BrainRequest/Response` | ChienPX/TuTM |
| `policy` | Áp luật kênh | `PolicyEngine`, `ChannelPolicy`, `PolicyRule`, `PolicyAction`, `PolicyResult`, `PolicyViolation`. Xem [policy.md](policy.md) | HungDQ |
| `catalog` | Sản phẩm | `Product`, `ProductRepository`, `ProductController` | LucVT |
| `customer` | Khách + danh tính kênh | `Customer`, `ChannelIdentity`, các repository | LucVT |
| `identity`, `order`, `promotion` | **Mới có `package-info.java`** ghi kế hoạch | | LucVT |

## MessageRouter
- `handle(CanonicalMessage)`: kiểm tra idempotency → `brainClient.ask` → `policyEngine.apply(channel, reply)` → trả `BotReply`.
- Idempotency key là `CHANNEL:messageId`, lưu trong LRU map 10.000 phần tử **trong bộ nhớ**. Chạy nhiều instance thì không đúng nữa; S7 (US-29) sẽ chuyển sang Redis.
- Chưa lưu hội thoại (US-11, S2).

## Thêm kênh mới
1. Thêm giá trị vào enum `ChannelType` (owned hay không).
2. Tạo `shared/policies/<kenh>.yaml`. Thiếu file thì `PolicyEngine.apply` ném `IllegalStateException`.
3. Viết class implement `ChannelAdapter<P>`: chuyển payload của kênh thành `CanonicalMessage`, gọi `MessageRouter.handle`, rồi gửi `finalText` ra kênh.
4. Thêm kênh vào `web/src/shared/types.ts`, schema JSON và [api-contracts.md](api-contracts.md).

## Cấu hình (`application.yml`)
`omniai.policy-dir`, `omniai.brain.url`, `omniai.brain.timeout` (10s), `omniai.web.allowed-origins`. Datasource đọc từ env `DB_*`.

## Test hiện có
- `policy/PolicyEngineTest`: link, SĐT, chặn tên kênh khác (Shopee/TikTok), WEB giữ link, làm mềm claim y khoa, giá tiền không bị tưởng là SĐT, "giá trị" không bị tưởng là "trị".
- `channel/MessageRouterTest`: cùng câu trả lời nhưng mỗi kênh lọc khác nhau, trùng messageId chỉ trả lời 1 lần, brain lỗi thì handoff. Dùng mock BrainClient.
- `OmniAiApplicationTests`: Flyway + pgvector trên Testcontainers.

## Quy ước
- Comment/Javadoc tiếng Việt, ghi `Owner:` ở class chính.
- Dùng record cho DTO, Lombok `@Getter @Setter` cho entity.
- Mỗi luật policy mới phải có test khớp đúng **và** test không khớp nhầm.
