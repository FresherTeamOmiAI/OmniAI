# Đối chiếu API Shopee dùng cho simulator

> Lấy ngày 2026-11-11. Owner: ChienPX. Phục vụ T-50.3 và T-51.1.
> Trang `open.shopee.com` không tải được trong lúc làm, nên mẫu dưới đây theo hợp đồng Open Platform v2 đã công bố: chữ ký push, `get_order_detail`, `update_price`, `update_stock`.

Nguồn:

- Chữ ký API shop: `partner_id + path + timestamp + access_token + shop_id`, HMAC-SHA256 hex, khóa `partner_key`. Timestamp hết hạn sau 5 phút. https://open.shopee.com/documents/v2/v2.public.get_access_token?module=104&type=1
- Push: `callbackUrl + "|" + rawBody`, HMAC-SHA256 hex, gửi ở header `Authorization`. Không parse rồi stringify lại trước khi ký. https://open.shopee.com/documents/v2/v2.push_mechanism?module=105&type=1
- Đơn: push code `3` chỉ báo đổi trạng thái. Chi tiết lấy bằng `GET /api/v2/order/get_order_detail`. https://open.shopee.com/documents/v2/v2.order.get_order_detail?module=94&type=1
- Giá: `POST /api/v2/product/update_price`. https://open.shopee.com/documents/v2/v2.product.update_price?module=89&type=1
- Tồn: `POST /api/v2/product/update_stock`. https://open.shopee.com/documents/v2/v2.product.update_stock?module=89&type=1

Push đơn dùng field `ordersn`. Chi tiết đơn dùng `order_sn`. Đó là tên trong tài liệu Shopee, không đổi cho khớp nhau.

| Việc | Path simulator | Ghi chú |
|---|---|---|
| Nhận giá | `POST /api/v2/product/update_price` | `price_list[].original_price`. Catalog không có biến thể nên `model_id` = 0 |
| Nhận tồn | `POST /api/v2/product/update_stock` | `stock_list[].seller_stock[].stock` |
| Xem số đã nhận | `GET /api/simulator/shopee/items/{itemId}` | Chỉ để kiểm tra, không có trên API Shopee |
| Phát đơn | `POST /api/simulator/shopee/orders` | Tạo đơn, trả raw body push code 3 và `Authorization` |
| Lấy chi tiết | `GET /api/v2/order/get_order_detail?order_sn_list=` | Tối đa dùng danh sách `order_sn` cách nhau bởi dấu phẩy |

Sai chữ ký thì body có `"error": "error_sign"` và số đã lưu không đổi. Khóa test nằm ở `omniai.channels.shopee` trong `application.yml`.
