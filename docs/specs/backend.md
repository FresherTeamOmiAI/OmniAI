# Backend (Spring Boot)

> Cập nhật: 2026-11-11 · commit c5ffbd9 · T-06.8
> Nguồn: `backend/pom.xml`, `backend/src/main/java/com/omniai/**`, `backend/src/test/**`

Spring Boot 4.1.2, Java 21, Maven (chưa có `mvnw`). Starter đang dùng: webmvc, websocket, validation, data-jpa, flyway (+postgresql), actuator, security, lombok. Test dùng spring-boot-starter-test, webmvc-test (MockMvc), security-test + Testcontainers (postgresql). Kiến trúc modular monolith: mỗi package là một module.

## Package `com.omniai.*`

| Package | Trách nhiệm | File chính | Owner |
|---|---|---|---|
| `common` | Kiểu dùng chung giữa các module; bảo mật | `CanonicalMessage`, `BotReply`, `ChannelType`, `SecurityConfig` | TuTM (`SecurityConfig`: LucVT) |
| `channel` | Luồng chung + adapter từng kênh | `ChannelAdapter<P>`, `MessageRouter` | TuTM |
| `channel.web` | Kênh WEB qua WebSocket | `WebChatAdapter` (là TextWebSocketHandler), `WebSocketConfig`, `WebChatInbound` | TuTM |
| `channel.simulator` | Giả lập kênh bất kỳ (bench, khi chưa có API sàn) | `SimulatorController`, `SimulatorAdapter` (record, không phải bean), `SimulatorRequest` | TuTM |
| `brain` | Client gọi ai-brain | `BrainClient` (interface để mock), `HttpBrainClient` (RestClient), `BrainRequest/Response` | ChienPX/TuTM |
| `policy` | Áp luật kênh | `PolicyEngine`, `ChannelPolicy`, `PolicyRule`, `PolicyAction`, `PolicyResult`, `PolicyViolation`. Xem [policy.md](policy.md) | HungDQ |
| `catalog` | Sản phẩm: API đọc cho store/AI, API admin | `Product`, `ProductRepository`, `ProductController` (đọc, chỉ active), `AdminProductController` + `ProductService` (CRUD admin), `ProductRequest`, `CatalogVocabulary` (luật dữ liệu), `ProductErrors`, `AdminProductExceptionHandler` (ProblemDetail) | LucVT |
| `customer` | Khách + danh tính kênh | `Customer`, `ChannelIdentity`, các repository | LucVT |
| `identity`, `order`, `promotion` | **Mới có `package-info.java`** ghi kế hoạch | | LucVT |

## MessageRouter
- `handle(CanonicalMessage)`: kiểm tra idempotency → `brainClient.ask` → `policyEngine.apply(channel, reply)` → trả `BotReply`.
- Idempotency key là `CHANNEL:messageId`, lưu trong LRU map 10.000 phần tử **trong bộ nhớ**. Chạy nhiều instance thì không đúng nữa; S7 (US-29) sẽ chuyển sang Redis.
- Chưa lưu hội thoại (US-11, S2).

## Thêm kênh mới
1. Thêm giá trị vào enum `ChannelType` (owned hay không).
2. Tạo `shared/policies/<kenh>.yaml`. Thiếu file thì backend không khởi động: `PolicyEngine` báo kênh nào còn thiếu.
3. Viết class implement `ChannelAdapter<P>`: chuyển payload của kênh thành `CanonicalMessage`, gọi `MessageRouter.handle`, rồi gửi `finalText` ra kênh.
4. Thêm kênh vào `web/src/shared/types.ts`, schema JSON và [api-contracts.md](api-contracts.md).

## Kênh sàn (decisions #12, #22)
Shopee và TikTok Shop chạy **100% bằng simulator theo schema thật**; dự án không nối API thật của sàn (chốt 11/11). Simulator vẫn phải đủ 3 điều kiện sau, để hợp đồng giữa hub và sàn đúng như tài liệu của sàn:
1. JSON webhook vào và request/response gửi tin ra chép đúng mẫu trong tài liệu chính thức của sàn, ghi nguồn (URL) và ngày lấy. Lưu thành fixture test, ví dụ `backend/src/test/resources/channel/shopee/*.json`.
2. Adapter sàn gồm `toCanonical()` đọc đúng webhook thật, và một interface client gửi dữ liệu ra sàn. Hiện chỉ có bản simulated (gọi simulator, simulator lưu lại và trả response mẫu); không làm bản live.
3. Webhook có chữ ký thì simulator ký bằng khóa test và adapter luôn kiểm chữ ký, kể cả khi đang giả lập.

Simulator chung `/api/simulator/{channel}/messages` vẫn giữ cho bench và `/admin/policy`. Simulator theo schema sàn làm ở S2: Shopee do ChienPX (US-21), TikTok Shop do TuTM (US-25). Cả hai đọc tài liệu API của cả 2 sàn và ghi bảng đối chiếu vào `docs/channel-api/shopee.md`, `docs/channel-api/tiktok.md` (T-21.4, T-25.3).

## Đồng bộ hub ↔ kênh (trụ E, decisions #23, chưa có code)
Hub (backend) là nguồn dữ liệu gốc. Thiết kế chi tiết viết ở T-48.3 sau khi có bảng đối chiếu API.

| Chiều | Dữ liệu | US · Sprint |
|---|---|---|
| Đi (hub → sàn) | Sản phẩm (tên, mô tả, ảnh, thành phần) | US-49 S3 (Shopee), US-50 S4 (TikTok Shop) |
| Đi | Giá theo kênh, tồn kho | US-50 S4 |
| Đi | Khuyến mãi (voucher, flash sale) | US-52 S6, cần US-33 (S5) |
| Đi | Trạng thái xử lý đơn (xác nhận, hủy) | US-53 S6 |
| Về (sàn → hub) | Hội thoại chăm sóc khách hàng | US-21, US-25 (S2), lưu ở US-11 (S2), xem ở US-14 (S3) |
| Về | Đơn hàng, kéo theo trừ tồn kho | US-51 S5 |
| Về | Trạng thái duyệt/vi phạm sản phẩm | US-54 S7 |
| Về | Đánh giá sản phẩm | US-55 S8 |

Quy ước: thay đổi trên hub đi qua hàng đợi đồng bộ, mỗi kênh một connector, gửi lỗi thì thử lại và không gửi trùng (US-49). Mọi webhook từ sàn đều kiểm chữ ký và chống trùng. Nội dung đẩy ra sàn phải qua luật của kênh (US-54).

## Bảo mật (`common/SecurityConfig.java`)
- Chỉ `/api/admin/**` cần đăng nhập (role ADMIN, HTTP Basic, 1 tài khoản in-memory từ `omniai.admin.*`, SRS Q-01). Mọi đường khác `permitAll`: store, simulator, `/ws/chat`, actuator.
- Stateless, tắt CSRF/form login/logout. `ADMIN_PASSWORD` rỗng thì không khởi động; còn là `admin` (mặc định dev) thì log WARN.
- Thêm endpoint cần khóa thì đặt dưới `/api/admin/`. US-13 (S3) thay tài khoản in-memory bằng tài khoản thật.

## Cấu hình (`application.yml`)
`omniai.policy-dir`, `omniai.brain.url`, `omniai.brain.timeout` (10s), `omniai.web.allowed-origins`, `omniai.admin.username/password` (env `ADMIN_USERNAME`/`ADMIN_PASSWORD`, mặc định `admin`/`admin`). Datasource đọc từ env `DB_*`.

## Test hiện có
- `policy/PolicyEngineTest`: link, SĐT, chặn tên kênh khác (Shopee/TikTok), WEB giữ link, làm mềm claim y khoa, giá tiền không bị tưởng là SĐT, "giá trị" không bị tưởng là "trị". File YAML sai (11 ca) hoặc thiếu kênh thì lỗi lúc khởi động; cắt `max_length` không tách emoji. Chạy bộ ca dùng chung với bench `shared/policies/tests/cases.yaml` trên cả 3 kênh.
- `channel/MessageRouterTest`: cùng câu trả lời nhưng mỗi kênh lọc khác nhau, trùng messageId chỉ trả lời 1 lần, brain lỗi thì handoff. Dùng mock BrainClient.
- `OmniAiApplicationTests`: Flyway + pgvector + catalog 80 SKU trên Testcontainers.
- `catalog/AdminProductApiTest` (MockMvc): 401 khi thiếu/sai Basic; store, simulator, actuator vẫn mở; list admin gồm SKU ẩn; tạo (chuẩn hóa, 201), trùng SKU 409, validate 400 theo field, sửa và ẩn/hiện phản ánh ngay ở `/api/products`, không đổi SKU. Mỗi test rollback (`@Transactional`).
- Container Postgres dùng chung qua `TestcontainersConfig` (`@Import`); test `@SpringBootTest` mới cũng import class này.
- Test tay API admin: Postman collection `backend/http/OmniAI-admin-products.postman_collection.json` (chạy được bằng newman) và `backend/http/admin-products.http` (REST Client). Hướng dẫn và bảng TC: `docs/testcases/US-08.md`.
- `channel/web/WebChatAdapterTest`: toCanonical tạo UUID khi messageId rỗng/blank, giữ nguyên id nếu có; định tuyến đúng CanonicalMessage; gửi BotReply qua WebSocket; tin rỗng/JSON hỏng thì trả `invalid_message` mà không đóng session; đóng session khi lỗi transport.
- `channel/web/WebChatSocketIT`: WebSocket thật (`RANDOM_PORT` + `TestcontainersConfig`, `@MockitoBean BrainClient`) dùng `StandardWebSocketClient`; tin hợp lệ thì nhận BotReply đúng messageId, JSON hỏng/text rỗng thì nhận `invalid_message`.
- Surefire trong `pom.xml` include thêm `**/*IT.java`, nên `mvn test` chạy luôn test tích hợp (cần Docker).
- `channel/MessageRouterTest`: cùng câu trả lời nhưng mỗi kênh lọc khác nhau, trùng messageId chỉ trả lời 1 lần, phân biệt key theo kênh, làm mềm claim y khoa trên WEB, brain lỗi thì handoff. Dùng mock BrainClient.
- `policy/PolicyEngineTest`: link, SĐT, chặn tên kênh khác (Shopee/TikTok), WEB giữ link, làm mềm claim y khoa, giá tiền không bị tưởng là SĐT, "giá trị" không bị tưởng là "trị".
- `OmniAiApplicationTests`: Flyway + pgvector trên Testcontainers.

## Quy ước
- Comment/Javadoc tiếng Việt, ghi `Owner:` ở class chính.
- Dùng record cho DTO, Lombok `@Getter @Setter` cho entity.
- Mỗi luật policy mới phải có test khớp đúng **và** test không khớp nhầm.
