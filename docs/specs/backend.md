# Backend (Spring Boot)

> Cập nhật: 2026-11-04 · commit 3922a5c
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

## Kênh sàn (quyết định #12)
Shopee và TikTok Shop chạy bằng simulator **theo schema thật** cho tới khi sàn duyệt app. Phải đủ 3 điều kiện để khi được duyệt chỉ cần đổi cấu hình và khóa, không sửa adapter hay `MessageRouter`:
1. JSON webhook vào và request/response gửi tin ra chép đúng mẫu trong tài liệu chính thức của sàn, ghi nguồn (URL) và ngày lấy. Lưu thành fixture test, ví dụ `backend/src/test/resources/channel/shopee/*.json`.
2. Adapter sàn gồm `toCanonical()` đọc đúng webhook thật, và một interface client gửi tin có 2 bản: simulated (giữ tin trong bộ nhớ, trả response mẫu) và live (gọi API sàn). Chọn theo từng sàn bằng cấu hình, ví dụ `omniai.channels.shopee.mode: simulator|live`.
3. Webhook có chữ ký thì simulator ký bằng khóa test và adapter luôn kiểm chữ ký, kể cả khi đang giả lập.

Simulator chung `/api/simulator/{channel}/messages` vẫn giữ cho bench và `/admin/policy`. Simulator theo schema sàn làm trong US-21 (S5) và US-25 (S6).

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
