# Đối chiếu API Shopee dùng cho simulator

> Đọc lại trên open.shopee.com ngày 2026-11-11. Owner: ChienPX. Phục vụ T-50.3 và T-51.1.

Nguồn đã mở:

- Chữ ký Shop API: base string `partner_id + api path + timestamp + access_token + shop_id`, HMAC-SHA256 hex, khóa partner key. Timestamp hết hạn sau 5 phút. https://open.shopee.com/developer-guide/16
- Push: `url + "|" + requestBody`, HMAC-SHA256 hex, so với header `Authorization`. https://open.shopee.com/developer-guide/18
- `order_status_push`, code `3`. Payload mẫu có `data.items`, `data.ordersn`, `data.status`, `data.completed_scenario`, `data.update_time`, rồi `shop_id`, `code`, `timestamp`. https://open.shopee.com/push-mechanism/5
- Trạng thái đơn: `UNPAID`, `PENDING`, `READY_TO_SHIP`, `PROCESSED`, `RETRY_SHIP`, `SHIPPED`, `TO_CONFIRM_RECEIVE`, `IN_CANCEL`, `CANCELLED`, `TO_RETURN`, `COMPLETED`. https://open.shopee.com/developer-guide/31
- Chi tiết đơn: `GET /api/v2/order/get_order_detail`. `order_sn`, `region`, `currency`, `cod`, `order_status`, `message_to_seller`, `create_time`, `update_time`, `days_to_ship`, `ship_by_date` trả mặc định. `item_list` và `total_amount` chỉ trả khi gọi kèm `response_optional_fields`. `order_sn_list` tối đa 50. https://open.shopee.com/documents/v2/v2.order.get_order_detail?module=94&type=1
- Giá: `POST /api/v2/product/update_price`. `price_list` từ 1 đến 50. `model_id` không bắt buộc, `0` nếu không có biến thể. `original_price` là float; VN chỉ được số nguyên. https://open.shopee.com/documents/v2/v2.product.update_price?module=89&type=1
- Tồn: `POST /api/v2/product/update_stock`. `stock_list` từ 1 đến 50, mỗi dòng có `seller_stock[]` với `location_id` (không có kho thì không gửi; mẫu tài liệu dùng `"-"`) và `stock` int64. https://open.shopee.com/documents/v2/v2.product.update_stock?module=89&type=1

Push đơn dùng field `ordersn`. Chi tiết đơn dùng `order_sn`. Đó là tên trong tài liệu Shopee, không đổi cho khớp nhau.

| Việc | Path simulator | Ghi chú |
|---|---|---|
| Nhận giá | `POST /api/v2/product/update_price` | Ghi hết `price_list` (1–50). Response có `warning`, `failure_list`, `success_list` |
| Nhận tồn | `POST /api/v2/product/update_stock` | Ghi hết `seller_stock`. Thiếu `location_id` thì lưu `"-"` như mẫu tài liệu |
| Xem số đã nhận | `GET /api/simulator/shopee/items/{itemId}` | Chỉ để kiểm tra, không có trên API Shopee |
| Phát đơn | `POST /api/simulator/shopee/orders` | Không phải API Shopee. Tạo đơn và trả raw body `order_status_push` code 3 |
| Lấy chi tiết | `GET /api/v2/order/get_order_detail` | `item_list` và `total_amount` chỉ có khi `response_optional_fields` xin hai field đó |
| Khách nhắn vào | `POST /api/simulator/shopee/webchat` | Không phải API Shopee. Tạo tin và trả raw body `webchat_push` code 10 |
| Shop trả lời | `POST /api/v2/sellerchat/send_message` | Bản giả lập. Xem mục Chat bên dưới |
| Đọc hội thoại | `GET /api/v2/sellerchat/get_message` | Bản giả lập |
| Danh sách hội thoại | `GET /api/v2/sellerchat/get_conversation_list` | Bản giả lập |

Sai chữ ký thì body có `"error": "error_sign"` và số đã lưu không đổi. Khóa test nằm ở `omniai.channels.shopee` trong `application.yml`.

## Chat — tài liệu giả lập

Trang `v2.sellerchat.send_message` trên open.shopee.com, ngày 2026-11-11, trả **You have no permission of this document** cả khi đã đăng nhập. Nhóm Chat không có trong cây tài liệu của tài khoản đó. Phần dưới không phải schema đã đọc trên trang đó.

Hai nguồn dùng để dựng cho gần Shopee:

1. **Tin khách gửi vào** là push công khai `webchat_push`, code `10`. Đọc ngày 2026-11-11 từ snapshot tài liệu push (bản 2026-09-07, bảng field cập nhật đến 2025-04-18). Mẫu JSON gốc nằm ở `backend/src/test/resources/channel/shopee/webchat-push-message.json`. Timeout 2 giây, retry 1 / 2 / 3, guaranteed delivery. Simulator ký push và trả body, không tự POST sang hub.
2. **Shop gửi và đọc tin** dựng theo body mà SDK công khai vẫn mô tả (`to_id`, `message_type`, `content`) và theo field của push ở trên, để cùng một tin đọc lại được. Trang chính thức của ba API này chưa đọc được.

`to_id` phía shop trong push lấy bằng `shop_id` test. Open Platform không công bố user id của shop trong phần đã đọc.

### Push code 10 — tin khách

`data.type` = `message`. `data.region` = `VN`. `data.content` gồm các field của mẫu, cộng bốn field bảng push thêm ngày 2025-04-18: `business_type` (0 = buyer–seller), `to_shop_id`, `from_shop_id` (0 vì người gửi là khách), `status` (`user_chat`).

`message_type` simulator nhận: `text` (`content.text`), `item` (`content.item_id`), `order` (`content.order_sn` phải là đơn đã có trong `order_shopee`). Bảng push còn kể `video`, `image`, `faq_liveagent`; ba loại đó chưa giả lập.

Phát tin:

```json
POST /api/simulator/shopee/webchat
{"from_id":165105353,"from_user_name":"nguyen.a","text":"Đơn của tôi đang ở đâu?"}
```

`message_type` bỏ trống thì là `text`. `order` gửi kèm `order_sn`. `item` gửi kèm `item_id`.

### Shop API giả lập

Query chung giống API shop khác: `partner_id`, `timestamp`, `access_token`, `shop_id`, `sign`.

`POST /api/v2/sellerchat/send_message`

```json
{"to_id":165105353,"message_type":"text","content":{"text":"Đơn đang được chuẩn bị"}}
```

`content` theo loại: `text`, `item_id`, hoặc `order_sn`. Response `response` có `message_id`, `to_id`, `message_type`, `content`, `conversation_id`, `created_timestamp`. Tin shop có `status` = `normal`. Không tạo push.

`GET /api/v2/sellerchat/get_message?conversation_id=&page_size=&offset=`

`page_size` mặc định 25, tối đa 60. `offset` là số tin bỏ qua, thứ tự cũ đến mới. `page_result.next_offset` rỗng khi hết trang. Không có hội thoại thì `error_param`.

`GET /api/v2/sellerchat/get_conversation_list?page_size=&direction=`

`direction` chỉ nhận `latest` hoặc `older`; cả hai trả một trang mới nhất. `to_id` là khách. `unread_count` tăng khi khách nhắn, về 0 khi shop trả lời. `page_result.next_cursor` luôn rỗng — chưa giả lập phân trang nano.

Cùng một khách (`from_id` / `to_id`) nằm trong một `conversation_id`. Id là chuỗi số do simulator sinh, không phải id Shopee thật.

## Database riêng

Simulator ghi vào database PostgreSQL tên `shopee`, cùng server với hub nhưng không dùng database `omniai`. Flyway của database này đọc `backend/src/main/resources/db/shopee`. App tự tạo database nếu chưa có, rồi migrate lúc khởi động. Biến môi trường: `SHOPEE_DB_URL`, `SHOPEE_DB_USERNAME`, `SHOPEE_DB_PASSWORD`.

| Bảng | Khớp với |
|---|---|
| `item_shopee` | `item_id` của update_price / update_stock |
| `price_list_shopee` | `price_list[]`: `model_id`, `original_price` |
| `seller_stock_shopee` | `stock_list[].seller_stock[]`: `location_id`, `stock` |
| `order_shopee` | `get_order_detail` → `order_list[]` |
| `order_item_shopee` | `item_list[]` |
| `push_shopee` | Push code 3. Cột `ordersn` là tên trong push; cột `order_sn` là tên trong chi tiết đơn |
| `conversation_shopee` | Hội thoại chat. `to_id` là khách |
| `message_shopee` | Một tin. `content` là JSON `text` / `item_id` / `order_sn` |
| `webchat_push_shopee` | Push code 10 của tin khách gửi vào |

Chưa làm các nhóm Authorization, Shop, Logistics, Payment, Promotion. `item_list` chỉ trả các field simulator đang có (`item_id`, `item_name`, `item_sku`, `model_id`, `model_name`, `model_sku`, `model_quantity_purchased`, `model_original_price`, `model_discounted_price`), không bịa ảnh, buyer hay phí vận chuyển.

## Nhóm tài liệu còn lại (T-21.4)

Giá, tồn và đơn đã đọc được schema ngày 2026-11-11. Ngày 2026-11-16 mở lại các URL dưới đây: trang chỉ trả khung "Shopee Open Platform", không có trường request/response trong HTML. Không chép schema từ trí nhớ. Chat seller ngày 2026-11-11 trả You have no permission.

| Nhóm | URL | Ngày | Đã giả lập |
|---|---|---|---|
| Chat / CSKH | https://open.shopee.com/documents/v2/v2.sellerchat.send_message | 2026-11-16 | Có, bản giả lập. Trang không hiện schema |
| Sản phẩm | https://open.shopee.com/documents/v2/v2.product.add_item?module=89&type=1 | 2026-11-16 | Chưa. Trang không hiện schema. US-49 |
| Giá | https://open.shopee.com/documents/v2/v2.product.update_price?module=89&type=1 | 2026-11-11 | Có. `POST /api/v2/product/update_price` |
| Tồn kho | https://open.shopee.com/documents/v2/v2.product.update_stock?module=89&type=1 | 2026-11-11 | Có. `POST /api/v2/product/update_stock` |
| Khuyến mãi | https://open.shopee.com/documents/v2/v2.discount.add_discount?module=97&type=1 | 2026-11-16 | Chưa. Trang không hiện schema. US-52 |
| Đơn hàng | https://open.shopee.com/documents/v2/v2.order.get_order_detail?module=94&type=1 | 2026-11-16 | Có push code 3 và lấy chi tiết, schema lấy ngày 2026-11-11. Hôm nay trang không hiện lại schema |
| Xác nhận / hủy đơn | https://open.shopee.com/documents/v2/v2.order.cancel_order?module=94&type=1 | 2026-11-16 | Chưa. Trang không hiện schema. US-53 |
| Duyệt sản phẩm | https://open.shopee.com/documents/v2/v2.product.get_item_list?module=89&type=1 | 2026-11-16 | Chưa. Trang không hiện schema. US-54 |
| Đánh giá | https://open.shopee.com/documents/v2/v2.product.get_comment?module=89&type=1 | 2026-11-16 | Chưa. Trang không hiện schema. US-55 |

