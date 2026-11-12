# TikTok Shop: bảng đối chiếu API cho simulator và đồng bộ hub ↔ kênh

> Cập nhật: 2026-11-11 · commit 0821a35 · Owner: TuTM · T-25.3 (US-25)
> DTO chi tiết từng API (request, response, mã lỗi): [tiktok-dto.md](tiktok-dto.md), sinh bằng `tools/channel-api/extract_tiktok_dto.py`.

## 1. Nguồn và độ tin cậy

| Nguồn | Ngày lấy | Ghi chú |
|---|---|---|
| TikTok Shop Partner Center, API Reference (`https://partner.tiktokshop.com/docv2`), các nhóm Authorization, Seller, Event, Customer Service, Products, Orders, Promotion, Fulfillment | 2026-11-11 | TuTM lưu 169 trang thành `.md` (thư mục `tịktok_api`, **không commit** vì là tài liệu của TikTok). Trang docv2 chỉ hiện khi chạy JavaScript, công cụ tự động không tải được. Path, version, scope, trường và mã lỗi trong file này lấy đúng từ các trang đó. |
| Hiểu biết chung về TikTok Shop Open API | | Dùng cho phần **thư mục không có**. Mỗi chỗ như vậy đánh dấu **(chưa đối chiếu)**. Code gom các giá trị này vào một chỗ: `TiktokErrorCode`, `TiktokSigner`, `WebhookType` (ở cả hub lẫn simulator). |

Các trang **chưa có trong thư mục**, cần mở docv2 để đối chiếu (mục 9):
- Get Access Token / Refresh Access Token (`docv2/page/678e3a3292b0f40314a92d75`)
- Sign your API request
- Webhooks overview + payload NEW_MESSAGE (`docv2/page/66143486ef8a1202dc323258`)
- Common error codes
- Rate limit
- Return & Refund

## 2. Hub và simulator

```
Seller (trình duyệt) --ủy quyền--> Simulator :8090 --redirect code--> Hub :8080 /api/channels/tiktok/oauth/callback
Buyer (bench, /sim/buyer/messages) -> Simulator --webhook NEW_MESSAGE (ký)--> Hub /api/channels/tiktok/webhook
Hub --Send Message (ký, access token)--> Simulator /customer_service/202606/conversations/{id}/messages
```

Simulator (`simulators/tiktok-shop`) giữ đúng path, version, tham số và envelope của TikTok. Hub chỉ cần đổi `TIKTOK_SIM_URL` là trỏ sang host thật (decisions #22: dự án không chạy live).

## 3. Quy ước chung của mọi API

- Base URL thật: `https://open-api.tiktokglobalshop.com`. Trên simulator: `http://localhost:8090`.
- Query bắt buộc: `app_key`, `timestamp` (Unix giây, UTC), `sign`. API dữ liệu shop còn cần `shop_cipher` (lấy từ Get Authorized Shops).
- Header: `content-type: application/json`, `x-tts-access-token: <access_token>`.
- Response luôn có dạng `{code, message, request_id, data}`. `code = 0` là thành công. Lỗi nghiệp vụ vẫn trả HTTP 200 kèm `code ≠ 0`. Hub log `request_id` để tra cứu.
- **Ký request (chưa đối chiếu):**
  1. Lấy mọi query param trừ `sign` và `access_token`, sắp theo key tăng dần.
  2. Nối thành chuỗi `key1value1key2value2...`, đặt `path` lên đầu.
  3. Nếu body không phải `multipart/form-data` thì nối thêm body thô.
  4. Bọc `app_secret` ở hai đầu, tính `HMAC-SHA256(key = app_secret)` rồi xuất hex chữ thường.
- **Phân trang:** dùng cursor `page_size` + `page_token`. Response trả `next_page_token`, chuỗi rỗng nghĩa là hết. Mỗi API có giới hạn riêng:

  | API | Giới hạn |
  |---|---|
  | Get Conversations | `page_size` 1–20 |
  | Get Conversation Messages | `page_size` 1–10, `sort_field` = `create_time` hoặc `index` |
  | Search Products | tổng kết quả ≤ 10.000, `seller_skus`/`sku_ids` ≤ 10 phần tử |
  | Search Promotion Activities | `page_token + page_size` < 10.000 |

- **Lọc:** khoảng thời gian dùng cặp `*_ge`/`*_le` (Products) hoặc `*_ge`/`*_lt` (Orders), tính bằng Unix giây. Ngoài ra lọc theo trạng thái (`status`, `order_status`, `audit_status`).
- **Thời gian:** CS và Orders tính bằng giây; Event (`create_time` của webhook) tính bằng **mili giây**. Hub phải đổi đơn vị theo từng API.

## 4. Ủy quyền OAuth2 và quản lý token

| Bước | API | Ghi chú |
|---|---|---|
| 1 | Seller mở URL ủy quyền (thật: `services.tiktokshop.com/open/authorize?service_id=...`; sim: `GET /oauth/authorize?app_key&state`) | Hub sinh `state` ngẫu nhiên và lưu lại để chống CSRF |
| 2 | Redirect về `redirect_uri?code=<auth_code>&state=` | Hub kiểm `state`. Code chỉ dùng được 1 lần |
| 3 | `GET /api/v2/token/get?app_key&app_secret&auth_code&grant_type=authorized_code` (host thật `auth.tiktok-shops.com`) **(chưa đối chiếu)** | Trả `access_token`, `access_token_expire_in` (thời điểm hết hạn, Unix giây), `refresh_token`, `refresh_token_expire_in`, `open_id`, `seller_name`, `seller_base_region`, `user_type`, `granted_scopes[]` |
| 4 | Get Authorized Shops `GET /authorization/202309/shops` | Lấy `shops[].id`, `cipher` |
| 5 | Update Shop Webhook `PUT /event/202309/webhooks` | Đăng ký nhận NEW_MESSAGE, NEW_CONVERSATION, SELLER_DEAUTHORIZATION, UPCOMING_AUTHORIZATION_EXPIRATION |
| Làm mới | `GET /api/v2/token/refresh?app_key&app_secret&refresh_token&grant_type=refresh_token` **(chưa đối chiếu)** | Trả về cặp token mới |

Hub lưu token trong bảng `channel_credential` (V8). Cách hub xử lý:
- Access token còn dưới 5 phút thì refresh trước khi gọi API.
- Gặp lỗi token thì refresh 1 lần rồi gọi lại.
- Mỗi shop chỉ 1 luồng được refresh tại một thời điểm, các luồng khác chờ kết quả.
- Refresh token hết hạn hoặc nhận webhook SELLER_DEAUTHORIZATION thì đặt `status` = `NEEDS_REAUTH` / `REVOKED` và ngừng gọi API.

Scope cần cấp: `seller.authorization.info`, `seller.customer_service`; từ S3 thêm `seller.product.basic`, `seller.product.write`, `seller.order.info`, `seller.promotion.info`, `seller.promotion.write`, `seller.fulfillment.basic`.

## 5. Webhook

- Đăng ký theo `event_type` (Event API). Có 17 loại, xem [tiktok-dto.md](tiktok-dto.md#update-shop-webhook).
- **Envelope (chưa đối chiếu):** `{type: int, tts_notification_id: string, shop_id: string, timestamp: int (giây), data: object}`.
  - Mã `type` mà hub dùng: 1 ORDER_STATUS_CHANGE, 6 SELLER_DEAUTHORIZATION, 7 UPCOMING_AUTHORIZATION_EXPIRATION, 13 NEW_CONVERSATION, 14 NEW_MESSAGE.
  - `data` của NEW_MESSAGE: `{message_id, conversation_id, type, content (chuỗi JSON), create_time, is_visible, index, sender{im_user_id, role}}`, cùng dạng với message trong Get Conversation Messages.
- **Chữ ký (chưa đối chiếu):** header `Authorization` = hex(`HMAC-SHA256(key = app_secret, msg = app_key + raw_body)`).
  - Hub kiểm trên **body thô**, so bằng thời gian hằng (`MessageDigest.isEqual`). Sai thì trả 401.
  - Hub từ chối `timestamp` lệch quá 5 phút để chống gửi lại (replay).
- **Phản hồi < 3 giây:** hub kiểm chữ ký rồi chống trùng, đưa việc vào hàng đợi async và trả **200 ngay**. Gọi AI Brain và gửi tin chạy ở luồng khác. Hàng đợi đầy thì trả 503 để sàn gửi lại.
- **Chống trùng (idempotency):**
  - Theo `tts_notification_id` (sàn gửi lại cùng sự kiện).
  - Theo `CHANNEL:message_id` trong `MessageRouter` (cùng tin đến qua 2 sự kiện khác nhau).
  - Hiện cả hai lưu trong bộ nhớ; S7 (US-29) chuyển sang Redis.
- **Retry của simulator:** không nhận 2xx hoặc quá 3 giây thì gửi lại tối đa 3 lần, có backoff. Mỗi lần giao đều ghi log ở `GET /sim/deliveries`.

## 6. Rate limit

- TikTok giới hạn QPS theo từng API và từng app **(chưa đối chiếu)**. CS còn có quota **10.000 request/ngày** (mã `45101004`, có trong thư mục).
- **Simulator:** tối đa **50 request đồng thời** cho mỗi app (`sim.rate-limit.max-concurrent`). Vượt thì trả HTTP 429 kèm `code` rate limit.
- **Hub:** tự giới hạn số request đồng thời dưới 50. Gặp 429 thì retry có backoff, tối đa 3 lần, sau đó trả handoff.

## 7. Danh sách API

Cột "Sim S2": ✅ = simulator có code ở S2. Các API khác chỉ ghi DTO, code theo US ở cột cuối.

| Nhóm | API | Method + path | Scope | Đi/Về | Sim S2 | US |
|---|---|---|---|---|---|---|
| Authorization | Get Access Token / Refresh | `GET /api/v2/token/get`, `/refresh` (chưa đối chiếu) | | | ✅ | 25 |
| Authorization | Get Authorized Shops | `GET /authorization/202309/shops` | seller.authorization.info | | ✅ | 25 |
| Seller | Get Active Shops | `GET /seller/202309/shops` | seller.shop.info | | | 49 |
| Event | Get / Update / Delete Shop Webhooks | `GET`/`PUT`/`DELETE /event/202309/webhooks` | seller.authorization.info | | ✅ | 25 |
| Customer Service | Get Conversations | `GET /customer_service/202309/conversations` | seller.customer_service | Về: hội thoại | ✅ | 25, 11 |
| Customer Service | Get Conversation | `GET /customer_service/202601/conversations/{id}` | seller.customer_service | Về | | 14 |
| Customer Service | Get Conversation Messages | `GET /customer_service/202309/conversations/{id}/messages` | seller.customer_service | Về | ✅ | 25, 11 |
| Customer Service | Create Conversation | `POST /customer_service/202309/conversations` | seller.customer_service | Đi | ✅ | 25 |
| Customer Service | Send Message | `POST /customer_service/202606/conversations/{id}/messages` | seller.customer_service | Đi: câu trả lời | ✅ | 25 |
| Customer Service | Mark Buyer Messages as Read | `POST /customer_service/202309/conversations/{id}/messages/read` | seller.customer_service | Đi | ✅ | 25 |
| Customer Service | Upload Image for Buyer Messages | `POST /customer_service/202309/images/upload` | seller.customer_service | Đi | | 47 |
| Customer Service | Get/Update Agent Settings | `GET`/`PUT /customer_service/202309/agents/settings` | seller.customer_service | | | 14 |
| Products | Search Products | `POST /product/202502/products/search` | seller.product.basic | Về: trạng thái duyệt | | 49, 54 |
| Products | Get Product | `GET /product/202309/products/{id}` | seller.product.basic | Về | | 49, 54 |
| Products | Create Product | `POST /product/202309/products` (có `idempotency_key`) | seller.product.write | Đi: sản phẩm | | 49 |
| Products | Edit / Partial Edit Product | `PUT /product/202509/products/{id}`, `POST .../partial_edit` | seller.product.write | Đi | | 49 |
| Products | Update Inventory | `POST /product/202309/products/{id}/inventory/update` | seller.product.write | Đi: tồn kho | | 50 |
| Products | Update Price | `POST /product/202309/products/{id}/prices/update` | seller.product.write | Đi: giá | | 50 |
| Products | Inventory Search | `POST /product/202309/inventory/search` | seller.product.basic | Về: đối soát tồn kho | | 50 |
| Products | Activate / Deactivate / Delete / Recover | `POST /product/202309/products/activate`, `/deactivate`, `DELETE /product/202309/products`, `POST .../recover` | seller.product.basic/delete | Đi: ẩn/hiện | | 49 |
| Products | Get Categories / Category Attributes / Category Rules / Brands / Recommend Category | `GET /product/202309/categories...`, `GET /product/202309/brands`, `POST /product/202309/categories/recommend` | seller.product.basic | | | 49 |
| Products | Upload Product Image | `POST /product/202309/images/upload` | seller.product.basic | Đi: ảnh | | 47, 49 |
| Orders | Get Order List | `POST /order/202309/orders/search` | seller.order.info | Về: đơn hàng | | 51 |
| Orders | Get Order Detail | `GET /order/202507/orders?ids=` | seller.order.info | Về | | 51 |
| Orders | Get Order Price Details | `GET /order/202407/orders/{id}/price_detail` | seller.order.info | Về | | 51 |
| Promotion | Create / Update / Get / Search Activity | `/promotion/202309/activities...` | seller.promotion.write/info | Đi: khuyến mãi | | 52 |
| Promotion | Update / Remove Activity Products, Deactivate, Republish | `PUT`/`DELETE .../activities/{id}/products`, `POST .../deactivate`, `POST /promotion/202607/.../republish` | seller.promotion.write | Đi | | 52 |
| Promotion | Get Coupon / Search Coupon List | `GET /promotion/202406/coupons/{id}`, `POST .../coupons/search` | seller.promotion.info | Về: voucher để bot nhắc | | 52 |
| Fulfillment | Search Package / Get Package Detail / Ship Package | `POST /fulfillment/202309/packages/search`, `GET .../packages/{id}`, `POST .../packages/{id}/ship` | seller.fulfillment.basic | Đi: trạng thái đơn | | 53 |

Các enum chính:
- `order_status`: UNPAID, ON_HOLD, AWAITING_SHIPMENT, PARTIALLY_SHIPPING, AWAITING_COLLECTION, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED.
- `activity_type`: FIXED_PRICE, DIRECT_DISCOUNT, FLASHSALE, SHIPPING_DISCOUNT, BUY_MORE_SAVE_MORE, GIFT_WITH_PURCHASE, BUY_X_GET_Y.
- Trạng thái sản phẩm: DRAFT, PENDING, FAILED, ACTIVATE, SELLER_DEACTIVATED, PLATFORM_DEACTIVATED, FREEZE, DELETED.
- `audit_status`: AUDITING, FAILED, APPROVED.
- Message `type` ở chiều về: TEXT, IMAGE, ALLOCATED_SERVICE, NOTIFICATION, BUYER_ENTER_FROM_*, PRODUCT_CARD, ORDER_CARD... Bot chỉ trả lời TEXT.

**Không đưa vào** (có trong thư mục nhưng không cần cho shop mỹ phẩm local ở VN):
- Global Product, Replicate: chỉ dành cho seller xuyên biên giới.
- FBT (Fulfillment by TikTok), Logistics, First Mile, customs, invoice: shop demo không giao hàng thật (SRS).
- Size chart, SKPP, Opportunity, SEO words, Image translation, Diagnose: tối ưu listing, ngoài phạm vi.
- Compliance (manufacturer, responsible person): yêu cầu riêng của EU.
- Blind box, POD, External order references: không có nghiệp vụ tương ứng.
- Get Customer Service Performance, Search Sessions, End Session: để dành cho dashboard nếu cần ở US-14.

## 8. Gợi ý thêm (thiếu hoặc chưa hợp lý) và lý do

| Gợi ý | Lý do |
|---|---|
| Bổ sung trang **Get/Refresh Access Token, Sign request, Webhooks overview + NEW_MESSAGE, Common error codes, Rate limit** | Simulator không chạy được nếu thiếu các phần này. Hiện dùng hiểu biết chung, đánh dấu chưa đối chiếu |
| Bổ sung **Return & Refund**: Cancel Order, Search Cancellations, webhook `CANCELLATION_STATUS_CHANGE` | Trụ E cần "đơn hàng kể cả xác nhận/hủy" (US-53), nhưng hủy đơn **không nằm** trong nhóm Orders |
| **Không có API đánh giá sản phẩm** trong các nhóm đã đọc | US-55 (đánh giá về hub, S8) cần xác minh lại. Không có API thì đổi phạm vi US-55 hoặc chỉ giả lập |
| Đăng ký `PRODUCT_AUDIT_STATUS_CHANGE`, `PRODUCT_STATUS_CHANGE` | Trạng thái duyệt/vi phạm sản phẩm về hub (US-54) mà không phải poll Search Products |
| **Bỏ qua tin của SHOP, ROBOT, SYSTEM, CUSTOMER_SERVICE** và tin `is_visible=false` | NEW_MESSAGE bắn cả cho tin shop vừa gửi. Không lọc thì bot tự trả lời chính mình (vòng lặp vô hạn) |
| Hub tự nhớ tin đã gửi (theo `message_id` của buyer) | Send Message không có idempotency key, nên retry sau timeout có thể làm khách nhận 2 tin |
| Sắp tin theo `index`, không theo thứ tự webhook đến | Webhook có thể đến sai thứ tự; tài liệu nói `index` tăng theo thời gian (không tăng chặt) |
| Kiểm `can_send_message` hoặc xử lý `45109001` trước khi gửi | Sàn không cho shop gửi tin trong mọi hội thoại (luật 30/60 ngày). Lỗi này thì chuyển nhân viên, không retry |
| Theo dõi quota CS 10.000 request/ngày (`45101004`) | Đồng bộ lại (backfill) hội thoại bằng Get Messages có thể dùng hết quota |
| Kiểm `timestamp` của webhook | Chữ ký đúng vẫn có thể bị gửi lại (replay) |
| Khóa refresh token theo shop | Hai luồng cùng refresh sẽ làm refresh token cũ mất hiệu lực, luồng chậm hơn bị lỗi |
| Dùng `idempotency_key` khi Create Product | Retry tạo sản phẩm không tạo ra bản trùng (US-49) |
| Cập nhật tồn kho bằng `sku_warehouse_stock_update_mode = INCREMENTAL` | Tài liệu khuyên **không** dùng FULL để đồng bộ tồn kho (FULL bỏ liên kết kho) |

## 9. Kịch bản biên (simulator phải giả lập được)

| Ca | Simulator trả về | Hub xử lý | Test |
|---|---|---|---|
| Access token hết hạn / sai | `code` token (chưa đối chiếu: 105002 / 105001) | Refresh 1 lần rồi gọi lại | `TiktokTokenManagerTest`, `/sim/faults` |
| Refresh token hết hạn | `code` refresh (chưa đối chiếu) | `NEEDS_REAUTH`, handoff | như trên |
| Sai scope | `code` scope (chưa đối chiếu: 105005) | Không retry, log lỗi, handoff | như trên |
| Sai chữ ký / `app_key` / timestamp lệch | `code` 106001 / 106002 (chưa đối chiếu) | Lỗi cấu hình, không retry | sim `GatewayTest` |
| invalid_params: thiếu `type`/`content`, TEXT > 2000 ký tự, `page_size` ngoài khoảng | `45101002` (CS) | Không retry, log | sim `CustomerServiceTest` |
| Nội dung nhạy cảm | `45101006` | Gửi `fallback_message` của kênh | sim |
| Không được gửi trong hội thoại | `45109001` | Handoff | sim |
| Vượt 50 request đồng thời | HTTP 429 | Backoff, tối đa 3 lần | sim `RateLimitTest` |
| Webhook trùng / trễ / hub trả 5xx | Gửi lại tối đa 3 lần | Chống trùng theo `tts_notification_id` | hub `TiktokWebhookControllerTest` |
| Brain chậm > 3 s | | Webhook vẫn trả 200 ngay, tin gửi sau | như trên |

**Xung đột tồn kho** (thiết kế cho US-50/51, chưa code ở S2):
- *Tình huống:* buyer đặt hàng (sàn giữ hàng cho đơn, bắn ORDER_STATUS_CHANGE) đúng lúc hub gọi Update Inventory với số lượng tuyệt đối. Update Inventory đặt `quantity` tuyệt đối cho từng kho, kể cả ở chế độ INCREMENTAL, nên giá trị hub tính trước khi có đơn sẽ ghi đè lên phần sàn vừa trừ.
- *Mã lỗi sàn đã có trong tài liệu:*
  - `12052561`: không đủ tồn kho cho phần đã giữ.
  - `12052165`: không đủ để giữ cho chiến dịch.
  - `12052393`: sản phẩm đang khóa vì khuyến mãi.
  - `12052037`: thao tác thất bại, cần kiểm tra lại tồn kho trước khi thử lại.
- *Simulator (S5):* tách `available` và `reserved`.
  - Đơn mới chuyển hàng từ `available` sang `reserved` dưới khóa.
  - Update Inventory với `quantity` nhỏ hơn `reserved` thì trả `12052561`.
  - Có cờ trễ để đặt đơn chen vào giữa lúc hub đọc và lúc hub ghi.
- *Hub:* hub là nguồn gốc, tồn kho đẩy lên sàn = tồn hub − đơn đã nhận nhưng chưa trừ.
  - Gặp `12052561`/`12052037`: gọi Inventory Search để đọc lại, tính lại rồi gửi lại (tối đa 3 lần).
  - Mỗi SKU chỉ 1 luồng đẩy tồn kho tại một thời điểm. Đơn mới đến thì đẩy lại.
  - Test: 2 luồng chạy song song (đặt đơn và cập nhật tồn kho), cuối cùng tồn sàn = tồn hub − đơn và không bao giờ âm.

## 10. Khác Shopee (để ChienPX đối chiếu với `shopee.md`, chưa đối chiếu)

| Điểm | TikTok Shop | Shopee Open Platform |
|---|---|---|
| Định danh shop | `shop_cipher` trong query | `shop_id` trong query |
| Chữ ký request | HMAC-SHA256, gồm path + query đã sắp xếp + body, bọc secret | HMAC-SHA256 trên `partner_id + path + timestamp + access_token + shop_id` |
| Version | Nằm trong path, theo từng API (`202309`, `202606`...) | `/api/v2/...` |
| Webhook | Đăng ký qua API Event theo từng shop | Cấu hình push trên console của app |
| Envelope | `{code, message, request_id, data}` | `{error, message, request_id, response}` |
