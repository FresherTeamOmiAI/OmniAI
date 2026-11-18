# Hợp đồng API

> Cập nhật: 2026-11-18 · T-49.1 `/api/admin/sync/**` trạng thái đồng bộ sản phẩm ra sàn; T-14.2 lọc `/api/conversations` theo ngày, vi phạm, handoff; `ConversationView` thêm số liệu; T-25.4 `MessageView.deliveryStatus`, `deliveryAttempts`; 2026-11-13 · T-04.1 ghi chú JSON sau V15; T-09.3 `/api/orders`; T-09.2 bộ lọc `GET /api/products`; T-04.1 `prices` theo kênh, `GET /api/admin/sales-channels` (decisions #32); trước đó US-11 (T-11.2), US-25, T-47.1
> Nguồn: `backend/.../channel/simulator/*`, `channel/web/*`, `channel/tiktok/*`, `catalog/ProductController.java`, `catalog/AdminProductController.java`, `catalog/ProductRequest.java`, `conversation/ConversationController.java`, `brain/*`, `common/*`, `ai-brain/app/main.py`, `ai-brain/app/schemas.py`, `shared/schemas/canonical-message.schema.json`, `web/src/shared/types.ts`

JSON phía backend dùng **camelCase**. Riêng hợp đồng backend → ai-brain dùng **snake_case**.

## Backend (cổng 8080)

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| POST | `/api/simulator/{channel}/messages` | `{messageId?, userId*, text*}` | `BotReply` | `channel` viết thường: web, shopee, tiktok. Bỏ trống `messageId` thì server tự sinh UUID; gửi trùng `messageId` thì nhận lại kết quả cũ. Dùng cho bench và trang `/admin/policy`. |
| POST | `/api/v2/product/update_price` | body Shopee `price_list` + query chữ ký | `{error, message, request_id, response}` | Simulator Shopee nhận giá (T-50.3). Sai chữ ký thì `error=error_sign`, số cũ giữ nguyên. |
| POST | `/api/v2/product/update_stock` | body Shopee `stock_list` + query chữ ký | như trên | Simulator Shopee nhận tồn kho (T-50.3). |
| GET | `/api/simulator/shopee/items/{itemId}` | | `{itemId, originalPrice, stock}` | Đọc `tbl_shopee_price_list` và `tbl_shopee_seller_stock`. |
| POST | `/api/simulator/shopee/orders` | `{item_id, item_sku, item_name, quantity, price, status?}` | `{order_sn, callback_url, authorization, raw_body, body}` | Phát đơn: body là push code 3, `authorization` là chữ ký push (T-51.1). |
| GET | `/api/v2/order/get_order_detail` | query `order_sn_list` + chữ ký | `{response.order_list[]}` | Chi tiết đơn simulator vừa phát. |
| POST | `/api/simulator/shopee/webchat` | `{from_id, from_user_name, text?}` | `{message_id, conversation_id, callback_url, authorization, raw_body, body}` | Phát tin khách. `body` là webchat_push code 10. Không trùng path bench `/messages`. |
| POST | `/api/v2/sellerchat/send_message` | `{to_id, message_type, content}` + chữ ký | `{response.message_id, conversation_id, content}` | Shop trả lời. Bản giả lập, xem [shopee.md](../channel-api/shopee.md). |
| GET | `/api/v2/sellerchat/get_message` | query `conversation_id` + chữ ký | `{response.messages[]}` | Đọc tin trong một hội thoại. |
| GET | `/api/v2/sellerchat/get_conversation_list` | query chữ ký | `{response.conversations[]}` | Danh sách hội thoại của shop. |
| GET | `/api/products?skinType=&category=&minPrice=&maxPrice=&page=&size=&sort=` | | `PageResponse<Product>` = `{items, page, size, total}` | Chỉ trả sản phẩm `active=true`. Lọc tuỳ chọn, kết hợp AND (T-09.2): `skinType` 1 loại da (`oily, dry, combination, sensitive, normal`, khớp nguyên giá trị trong `skinTypes`); `category` 1 danh mục; `minPrice`/`maxPrice` theo **giá web** (`prices.WEB`, VND, gồm cả mốc). Không phân biệt hoa thường, bỏ trống = không lọc. Giá trị ngoài từ vựng, giá âm, `minPrice > maxPrice` thì `400` ProblemDetail có `errors[{field, message}]`; sai kiểu số thì `400`. Mặc định size 20. |
| GET | `/api/products/sku/{sku}` | | `{sku, brand, name, category, prices, stock}` hoặc 404 | Giá theo kênh và tồn của SKU đang bán (US-15). AI Brain gọi endpoint này. SKU ẩn hoặc không có thì 404. |
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
| GET | `/api/admin/sales-channels` | | `[SalesChannel]` | `SalesChannel = {code, name, owned, active}`, kênh sở hữu trước. Form admin sinh ô giá theo danh sách này (V13) |

- `ProductRequest`: các field của `Product` trừ `id`, `updatedAt`. Bắt buộc: `sku, name, brand, category, skinTypes, ingredientsInci, sizeMl, paoMonths, prices, stock`.
- `prices`: object `{<mã kênh>: giá VND}`, vd. `{"WEB": 350000, "SHOPEE": 364000, "TIKTOK": 340000}` (thay `priceWeb/priceShopee/priceTiktok` từ V13, decisions #32). Phải có **mọi kênh đang active**; thiếu kênh hoặc mã kênh lạ thì `400` ở field `prices[<MÃ>]`, giá null/âm thì `400` ở `prices[<MÃ>]`. Mã kênh được viết hoa. Kênh không active không bắt buộc; kênh không gửi lên thì giữ giá cũ.
- Server chuẩn hóa trước khi kiểm: trim, Unicode NFC, `sku` viết hoa, `category`/`skinTypes`/`skinConcerns`/`usageTime` viết thường và bỏ trùng, INCI nối bằng `", "`, chuỗi rỗng thành `null`.
- Luật kiểm giống `tools/catalog/validate_catalog.py` (`catalog/CatalogVocabulary.java`): dạng SKU, bộ từ vựng ở [data-model.md](data-model.md), INCI ≥ 2 thành phần, giá/tồn kho ≥ 0, dung tích/PAO > 0, độ dài cột, tên/mô tả không có claim y khoa ("trị mụn", "đặc trị", "chữa"...).
- Lỗi dạng ProblemDetail (RFC 9457, `application/problem+json`): `{type, title, status, detail, instance, errors?}`. `400` sai dữ liệu có `errors: [{field, message}]`; `400` JSON hỏng hoặc sai kiểu; `404`; `409` trùng SKU.

## Đồng bộ sản phẩm ra sàn (`/api/admin/sync/**`, US-49, T-49.1)
Cần Basic ADMIN. Màn admin ở T-49.3.

| Method | Path | Response | Ghi chú |
|---|---|---|---|
| GET | `/api/admin/sync/channels` | `["SHOPEE", ...]` | Kênh có connector. S3 rỗng trên app thật (connector Shopee sau T-49.2) |
| GET | `/api/admin/sync/products/by-ids?ids=1,2,3` | `[SyncView]` | 1–100 id, cho 1 trang bảng sản phẩm. Sản phẩm/kênh chưa từng xếp hàng thì không có dòng |
| GET | `/api/admin/sync/products?status=&channel=&page=&size=` | `PageResponse<SyncView>` | Lọc `PENDING | SYNCED | FAILED` (không phân biệt hoa thường), mới cập nhật trước. `size` 1–100. Sai thì `400` |
| POST | `/api/admin/sync/products/{productId}/{channel}/retry` | `SyncView` | Về `PENDING`, `attempts = 0`, gửi ở lượt quét kế. Chưa có dòng thì `404` |
| POST | `/api/admin/sync/products/resync?channel=SHOPEE` | `{queued}` | Xếp lại mọi sản phẩm của kênh (kể cả nội dung không đổi). Kênh không có connector hoặc tạm ngừng thì `400` |

- `SyncView = {productId, channel, status, attempts, lastError?, externalId?, syncedAt?, nextAttemptAt, updatedAt}`. `externalId` = mã sản phẩm phía sàn sau lần tạo đầu. `attempts` = số lần đã gửi cho bản đang chờ (SYNCED: số lần của lượt thành công).
- Hợp đồng cho connector (`sync/ProductChannelConnector`): `push(ProductSnapshot, externalId)` tạo khi `externalId == null`, sửa khi có, trả mã phía sàn; ném `SyncException(retryable=false)` khi thử lại vô ích. `ProductSnapshot = {productId, channel, sku, name, brand, category, description, ingredientsInci, sizeMl, imageKeys[], imageUrls[], price (của kênh), stock, active}`.

## Đơn hàng (`/api/orders`, T-09.3, SRS UC-06)
Mở công khai như store (đơn S2 không gắn khách, không có dữ liệu cá nhân, decisions #34). Từ S3 đơn gắn khách đăng nhập thì phải khoá theo chủ đơn.

| Method | Path | Body | Trả về | Ghi chú |
|---|---|---|---|---|
| POST | `/api/orders` | `{items: [{productId*, quantity*}]}` | `201` + `Location`, `OrderView` | Tạo đơn **DEMO** trên kênh WEB: không thanh toán, không giao, **không trừ tồn kho** (G-04). 1–20 dòng, `quantity` 1–10; dòng trùng `productId` được gộp (tổng vẫn <= 10). Sản phẩm không có / đang ẩn → `400` ở `items[i].productId`; hết hàng / không đủ hàng / tổng > 10 → `400` ở `items[i].quantity` |
| GET | `/api/orders/{id}` | | `OrderView` | Không có thì `404` |

- `OrderView`: `id, status ("DEMO"), channel ("WEB"), items[{productId, sku, name, unitPrice, quantity, lineTotal}], total, createdAt, notice` (`notice` = "Đơn thử nghiệm: không thanh toán, không giao hàng."). `unitPrice`, `name`, `sku` là bản chụp lúc đặt.
- Lỗi theo ProblemDetail như API sản phẩm: `400` có `errors[{field, message}]` (field dạng `items[0].quantity`), JSON hỏng `400`, `404`.

## Hội thoại (`/api/conversations`, US-11, T-11.2)

Cần Basic ADMIN giống `/api/admin/**` (hội thoại là dữ liệu cá nhân, decisions #31); thiếu/sai thì `401`. Dữ liệu do `MessageRouter` ghi cho mọi kênh. Màn admin xem hội thoại ở US-14 (S3).

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| GET | `/api/conversations?channel=&from=&to=&hasViolation=&handoff=&page=&size=` | | `PageResponse<ConversationView>` | Mới nhất trước (`lastMessageAt` giảm dần). Bộ lọc kết hợp AND (T-14.2). `channel` không phân biệt hoa thường (`web`, `SHOPEE`), bỏ trống = mọi kênh, kênh lạ `400`. `from`/`to`: ngày `yyyy-MM-dd` **giờ VN**, gồm cả 2 mốc, lấy hội thoại có hoạt động trong khoảng (`startedAt` < hết ngày `to`, `lastMessageAt` ≥ đầu ngày `from`); sai định dạng hoặc `from > to` thì `400`. `hasViolation=true`: có ≥ 1 câu OUT bị chặn hoặc có luật vi phạm (trong khoảng ngày nếu có). `handoff=true`: có ≥ 1 câu OUT `handoff`. `size` mặc định 20, 1–100, ngoài khoảng hoặc `page` < 0 thì `400`. Không nhận `sort` |
| GET | `/api/conversations/{id}/messages?page=&size=` | | `PageResponse<MessageView>` | Theo thời gian tăng dần. `size` mặc định 50, tối đa 100. Không có hội thoại thì `404` |

- `ConversationView = {id, channel, conversationId, channelUserId, customerId?, startedAt, lastMessageAt, messageCount, flaggedCount, handoffCount, lastText?}`. `flaggedCount` = số câu OUT bị chặn hoặc có vi phạm, `handoffCount` = số câu OUT chuyển nhân viên, `lastText` = tin cuối (IN hoặc OUT); 3 trường này tính bằng 1 câu SQL cho cả trang. `conversationId` = `CanonicalMessage.conversationId` (id phía kênh), `id` là id trong hub dùng cho `/messages`. `messageCount` đếm cả IN lẫn OUT.
- `MessageView = {id, direction: IN|OUT, messageId, text, rawText?, violations[], blocked, handoff, deliveryStatus?, deliveryAttempts, createdAt}`. `deliveryStatus` chỉ có ở câu OUT của kênh gửi async (TikTok): `RETRYING` đang thử lại, `SENT` sàn đã nhận, `FAILED` không gửi được (kèm `handoff=true`); null với kênh đồng bộ (T-25.4). IN: `text` là tin khách, `rawText` null. OUT: `text` = `finalText` (khách nhận), `rawText` = câu AI gốc, `violations[]` = `PolicyViolation`. `messageId` của OUT là id tin khách mà nó trả lời.
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

Simulator Shopee đã nhận giá, tồn kho, phát đơn và phát chat: [shopee.md](../channel-api/shopee.md). Hub nhận push tại `POST /api/channels/shopee/push` (chữ ký `callbackUrl|body`), áp luật Shopee rồi ghi tin trả lời vào simulator. Chat seller là bản giả lập vì trang Open Platform không cấp quyền đọc. Chưa có: tài khoản thật (US-13, S3), API đồng bộ hub ↔ kênh (trụ E, từ S3, xem [backend.md](backend.md)).

## Kiểu dữ liệu chung (`com.omniai.common`, `web/src/shared/types.ts`)

- `ChannelType`: `WEB` (owned = kênh shop sở hữu, được gửi link/so sánh kênh) | `SHOPEE, TIKTOK` (sàn, chạy bằng simulator). Telegram và Messenger đã bỏ ngày 11/11 (decisions #22).
- `CanonicalMessage(messageId, channel, channelUserId, customerId?, conversationId, text, receivedAt, metadata)`. JSON Schema trong `shared/schemas/canonical-message.schema.json`. Hiện `conversationId` = `<kênh thường>:<userId>`, `customerId` luôn null (chưa liên kết danh tính).
- `BotReply(messageId, channel, rawText, finalText, violations[], blocked, handoff)`:
  - `rawText`: câu AI nói. `finalText`: câu khách nhận sau policy.
  - `violations[]`: `PolicyViolation(ruleId, action: BLOCK|REDACT|REPLACE, matched)`.
  - `blocked`: cả câu bị thay bằng `fallback_message`. `handoff`: cần nhân viên.
- `Product`: `id, sku, name, brand, category, skinTypes (csv), skinConcerns (csv)?, usageTime ("am"|"pm"|"am,pm")?, description?, ingredientsInci (csv), sizeMl, paoMonths, prices ({<mã kênh>: VND}), stock, active, barcode?, sourceUrl?, imageKey?, imageSourceUrl?, imageLicenseNote?, imageUrl?, updatedAt` (V9, T-47.1). `imageUrl` không lưu DB: backend ghép `<SUPABASE_URL>/storage/v1/object/public/<SUPABASE_BUCKET>/<imageKey>` (`catalog/ProductImages.java`), `null` khi chưa có ảnh hoặc chưa cấu hình `SUPABASE_URL`. `ProductRequest` **không** có các trường nguồn/ảnh: admin tạo thì để trống, sửa thì giữ nguyên. Nơi hiển thị ảnh phải ghi nguồn Open Beauty Facts (ODbL, CC BY-SA 3.0). Dấu `?` = có thể null. Bộ giá trị hợp lệ xem [data-model.md](data-model.md). Kiểu TS: `web/src/shared/types.ts` (dùng chung cho store và admin). Từ V15 (decisions #35) các field giữ nguyên tên và kiểu nhưng đọc từ bảng chuẩn hoá: `skinTypes`/`skinConcerns` trả theo `sort_order` của bảng tra cứu (vd. `oily,combination,normal`), `ingredientsInci` theo thứ tự trên nhãn và đã bỏ trùng, `imageKey`/`imageSourceUrl`/`imageLicenseNote` là của ảnh chính. Hãng và thành phần mới gửi qua `ProductRequest` được tự thêm vào bảng tra cứu.

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
