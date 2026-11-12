# Hợp đồng API

> Cập nhật: 2026-11-12 · commit e9b9d0f · US-11 (T-11.2); trước đó US-25
> Nguồn: `backend/.../channel/simulator/*`, `channel/web/*`, `channel/tiktok/*`, `catalog/ProductController.java`, `catalog/AdminProductController.java`, `catalog/ProductRequest.java`, `conversation/ConversationController.java`, `brain/*`, `common/*`, `ai-brain/app/main.py`, `ai-brain/app/schemas.py`, `shared/schemas/canonical-message.schema.json`, `web/src/shared/types.ts`

JSON phía backend dùng **camelCase**. Riêng hợp đồng backend → ai-brain dùng **snake_case**.

## Backend (cổng 8080)

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| POST | `/api/simulator/{channel}/messages` | `{messageId?, userId*, text*}` | `BotReply` | `channel` viết thường: web, shopee, tiktok. Bỏ trống `messageId` thì server tự sinh UUID; gửi trùng `messageId` thì nhận lại kết quả cũ. Dùng cho bench và trang `/admin/policy`. |
| GET | `/api/products?skinType=&page=&size=` | | `PageResponse<Product>` = `{items, page, size, total}` | Chỉ trả sản phẩm `active=true`. `skinType` lọc kiểu "contains, không phân biệt hoa thường". Mặc định size 20. |
| GET | `/api/products/{id}` | | `Product` hoặc 404 | Sản phẩm `active=false` cũng trả 404 (SRS G-06). |
| WS | `/ws/chat` | text frame `{messageId, text}` | text frame `BotReply`, hoặc `{error:"invalid_message", messageId}` | Kênh WEB. Origin cho phép: `WEB_ALLOWED_ORIGINS`. JSON hỏng hoặc text rỗng/null thì server trả frame lỗi, không im lặng; `messageId` lấy từ tin gửi lên, là `null` nếu JSON hỏng. Session vẫn mở. Widget có timeout 12 s (lớn hơn `omniai.brain.timeout` 10 s): hết giờ thì báo "Shop đang bận". |
| GET | `/actuator/health`, `/actuator/info` | | | |

## Admin sản phẩm (`/api/admin/**`, US-08, T-08.1)

Cần header `Authorization: Basic` với `ADMIN_USERNAME`/`ADMIN_PASSWORD` (SRS Q-01), sai hoặc thiếu thì `401`. `401` có header `WWW-Authenticate: Basic`, trừ khi request gửi `X-Requested-With: XMLHttpRequest` (web admin luôn gửi để trình duyệt không bật hộp đăng nhập). Mọi đường khác vẫn mở. Ví dụ chạy tay: Postman `backend/http/OmniAI-admin-products.postman_collection.json`, REST Client `backend/http/admin-products.http` (hướng dẫn: `docs/testcases/US-08.md`).

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| GET | `/api/admin/products?q=&active=&page=&size=&sort=` | | `PageResponse<Product>` | **Gồm cả sản phẩm ẩn.** `q` tìm trong sku/tên (contains, không phân biệt hoa thường). `active` bỏ trống = tất cả. Mặc định size 20, sort `id`. Sort theo field lạ thì `400` |
| GET | `/api/admin/products/{id}` | | `Product` | Kể cả sản phẩm ẩn. Không có thì `404` |
| POST | `/api/admin/products` | `ProductRequest` | `201` + `Location`, `Product` | Trùng SKU thì `409`. `active` bỏ trống = `true` |
| PUT | `/api/admin/products/{id}` | `ProductRequest` | `Product` | Sửa toàn bộ. `sku` phải giữ nguyên, khác thì `400`. `active` bỏ trống = giữ nguyên |
| PATCH | `/api/admin/products/{id}/active` | `{active*}` | `Product` | Ẩn/hiện. **Không có DELETE** (G-07) |

- `ProductRequest`: các field của `Product` trừ `id`, `updatedAt`. Bắt buộc: `sku, name, brand, category, skinTypes, ingredientsInci, sizeMl, paoMonths, priceWeb, priceShopee, priceTiktok, stock`.
- Server chuẩn hóa trước khi kiểm: trim, Unicode NFC, `sku` viết hoa, `category`/`skinTypes`/`skinConcerns`/`usageTime` viết thường và bỏ trùng, INCI nối bằng `", "`, chuỗi rỗng thành `null`.
- Luật kiểm giống `tools/catalog/validate_catalog.py` (`catalog/CatalogVocabulary.java`): dạng SKU, bộ từ vựng ở [data-model.md](data-model.md), INCI ≥ 2 thành phần, giá/tồn kho ≥ 0, dung tích/PAO > 0, độ dài cột, tên/mô tả không có claim y khoa ("trị mụn", "đặc trị", "chữa"...).
- Lỗi dạng ProblemDetail (RFC 9457, `application/problem+json`): `{type, title, status, detail, instance, errors?}`. `400` sai dữ liệu có `errors: [{field, message}]`; `400` JSON hỏng hoặc sai kiểu; `404`; `409` trùng SKU.

## Hội thoại (`/api/conversations`, US-11, T-11.2)

Cần Basic ADMIN giống `/api/admin/**` (hội thoại là dữ liệu cá nhân, decisions #30); thiếu/sai thì `401`. Dữ liệu do `MessageRouter` ghi cho mọi kênh. Màn admin xem hội thoại ở US-14 (S3).

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| GET | `/api/conversations?channel=&page=&size=` | | `PageResponse<ConversationView>` | Mới nhất trước (`lastMessageAt` giảm dần). `channel` không phân biệt hoa thường (`web`, `SHOPEE`), bỏ trống = mọi kênh, kênh lạ `400`. `size` mặc định 20, 1–100, ngoài khoảng hoặc `page` < 0 thì `400`. Không nhận `sort` |
| GET | `/api/conversations/{id}/messages?page=&size=` | | `PageResponse<MessageView>` | Theo thời gian tăng dần. `size` mặc định 50, tối đa 100. Không có hội thoại thì `404` |

- `ConversationView = {id, channel, conversationId, channelUserId, customerId?, startedAt, lastMessageAt, messageCount}`. `conversationId` = `CanonicalMessage.conversationId` (id phía kênh), `id` là id trong hub dùng cho `/messages`. `messageCount` đếm cả IN lẫn OUT.
- `MessageView = {id, direction: IN|OUT, messageId, text, rawText?, violations[], blocked, handoff, createdAt}`. IN: `text` là tin khách, `rawText` null. OUT: `text` = `finalText` (khách nhận), `rawText` = câu AI gốc, `violations[]` = `PolicyViolation`. `messageId` của OUT là id tin khách mà nó trả lời.
- Lỗi `400`/`404` trả theo xử lý lỗi mặc định của Spring (`ResponseStatusException`), chưa phải ProblemDetail như admin sản phẩm.

## Kênh TikTok Shop (US-25)

Hub nói chuyện với sàn theo **đúng hợp đồng TikTok Shop** (snake_case, envelope `{code, message, request_id, data}`, ký HMAC-SHA256). API simulator đã code, DTO và mã lỗi: [../channel-api/tiktok.md](../channel-api/tiktok.md), [../channel-api/tiktok-dto.md](../channel-api/tiktok-dto.md). Endpoint của hub:

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| POST | `/api/channels/tiktok/webhook` | Body thô `{type, tts_notification_id, shop_id, timestamp, data}`, header `Authorization` = hex(HMAC-SHA256(app_secret, app_key + body)) | `200 {code:0, message:"success"}` | Sai/thiếu chữ ký, timestamp lệch > 5 phút: `401 {error}`. Body hỏng: `400`. Hàng đợi đầy: `503` (sàn gửi lại). Trùng `tts_notification_id`: vẫn 200, không xử lý lại. Brain và gửi tin chạy sau khi đã trả 200 |
| GET | `/api/channels/tiktok/oauth/callback?code&state` | | `{shops:[CredentialView]}` | Sàn redirect về sau khi seller đồng ý. `state` sai/hết hạn (10 phút)/đã dùng: `400 invalid_state`. Sàn lỗi: `502` |
| GET | `/api/admin/channels/tiktok/authorize` | Basic | `{authorizeUrl, stateExpiresAt}` | Mở `authorizeUrl` bằng trình duyệt để ủy quyền |
| GET | `/api/admin/channels/tiktok/credentials` | Basic | `[CredentialView]` | `CredentialView = {shopId, shopName, status, accessExpiresAt, refreshExpiresAt, scopes}`, **không** trả token |

Simulator (cổng 8090) có thêm API điều khiển `/sim/*` (không có trên sàn thật, JSON snake_case): `POST /sim/buyer/messages {buyer_user_id*, nickname?, text*, wait_ms?}` → `{conversation_id, message_id, tts_notification_id, new_conversation, reply?, reply_latency_ms?}`; `GET|POST /sim/faults` (`api_latency_ms, webhook_delay_ms, webhook_duplicate, sensitive_words[], revoked_scopes[], block_sending`); `POST /sim/tokens/expire?kind=access|refresh`; `GET /sim/deliveries`; `POST /sim/reset`.

Chưa có: API của simulator Shopee theo schema thật (US-21, S2), tài khoản thật (US-13, S3), API đồng bộ hub ↔ kênh (trụ E, từ S3, xem [backend.md](backend.md)).

## Kiểu dữ liệu chung (`com.omniai.common`, `web/src/shared/types.ts`)

- `ChannelType`: `WEB` (owned = kênh shop sở hữu, được gửi link/so sánh kênh) | `SHOPEE, TIKTOK` (sàn, chạy bằng simulator). Telegram và Messenger đã bỏ ngày 11/11 (decisions #22).
- `CanonicalMessage(messageId, channel, channelUserId, customerId?, conversationId, text, receivedAt, metadata)`. JSON Schema trong `shared/schemas/canonical-message.schema.json`. Hiện `conversationId` = `<kênh thường>:<userId>`, `customerId` luôn null (chưa liên kết danh tính).
- `BotReply(messageId, channel, rawText, finalText, violations[], blocked, handoff)`:
  - `rawText`: câu AI nói. `finalText`: câu khách nhận sau policy.
  - `violations[]`: `PolicyViolation(ruleId, action: BLOCK|REDACT|REPLACE, matched)`.
  - `blocked`: cả câu bị thay bằng `fallback_message`. `handoff`: cần nhân viên.
- `Product`: `id, sku, name, brand, category, skinTypes (csv), skinConcerns (csv)?, usageTime ("am"|"pm"|"am,pm")?, description?, ingredientsInci (csv), sizeMl, paoMonths, priceWeb, priceShopee, priceTiktok, stock, active, updatedAt`. Dấu `?` = có thể null. Bộ giá trị hợp lệ xem [data-model.md](data-model.md). Kiểu TS: `web/src/shared/types.ts` (dùng chung cho store và admin).

## AI Brain (cổng 8000)

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | | `{status, provider}` |
| POST | `/chat` | `ChatRequest` | `ChatResponse` |

- `ChatRequest` (Pydantic) = `BrainRequest` (Java record): `message_id, channel, channel_owned, user_id, customer_id?, text`. Tên sản phẩm không đi trong request. Brain tự đọc view `chatbot_catalog` bằng role `chatbot_reader` (`ai-brain/app/rules/chatbot_database_security.md`).
- `ChatResponse` = `BrainResponse`: `reply, provider, warnings[] (mã cảnh báo), handoff`. Mã cảnh báo: `conflict:<a>+<b>`, `pregnancy:<nhóm>` (luật thành phần); `grounding:price|stock|promo` (AI Brain đã bỏ số giá, tồn kho, khuyến mãi do LLM tự nêu, T-06.6).
- Backend gọi qua `HttpBrainClient` với timeout 10s (`omniai.brain.timeout`). Nếu lỗi thì trả `BrainResponse.unavailable()`: câu chờ + `handoff=true`, provider `"none"`.
- Đổi field ở một phía thì **phải sửa cả 2 phía**: `schemas.py` và `BrainRequest`/`BrainResponse`.

## Quy tắc khi đổi hợp đồng
1. Sửa cả nơi gửi lẫn nơi nhận (Java ↔ Python ↔ `web/src/shared/types.ts`).
2. Nếu đổi `CanonicalMessage` thì sửa JSON Schema trong `shared/schemas/`.
3. Cập nhật bảng trong file này và [status.md](status.md).
