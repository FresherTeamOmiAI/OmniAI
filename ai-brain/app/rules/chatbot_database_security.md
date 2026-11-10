# Chatbot chỉ được đọc catalog công khai

Tài liệu này là quy tắc của OmniAI, không phải mẫu database chung. Schema hiện có ba bảng trong schema `public`: `product`, `customer`, `channel_identity`. Chưa có `orders`, `users`, `passwords`, `payments`.

## Dữ liệu nào được đọc

| Nguồn | Chatbot |
|---|---|
| View `chatbot_catalog` | Được đọc. Cột: `brand`, `name`, `category`, `skin_types`, `skin_concerns`, `usage_time`, `price_web`, `price_shopee`, `price_tiktok`. Chỉ sản phẩm `active`. |
| `product.stock` | Không. Tồn kho vẫn chờ tool ở US-15. |
| `customer` | Không. Có email và số điện thoại. |
| `channel_identity` | Không. Có mã người dùng trên sàn và thời điểm consent. |

## Năm lớp, cả năm đều phải có

1. **Role riêng.** `chatbot_reader` không phải user `omniai`. User ứng dụng là owner, đọc được mọi bảng. Chatbot không dùng user đó.
2. **View.** `chatbot_catalog` có giá niêm yết của ba kênh, không có tồn kho, email, số điện thoại. View để `security_invoker = false`: role chatbot đọc view bằng quyền của owner, nhưng không được `SELECT` thẳng bảng gốc.
3. **Validator.** Mọi câu SQL đi qua `sql_guard.py` (SQLGlot) trước khi tới database. Một statement, chỉ `SELECT`, chỉ bảng `chatbot_catalog`, chỉ các cột của view, không `UNION`, không nhiều câu lệnh. Có `LIMIT` tối đa 100.
4. **Thực thi chỉ đọc.** Transaction read-only, `statement_timeout` 2 giây. Sai quyền thì PostgreSQL từ chối, dù validator bị lỗi.
5. **Prompt.** Model chỉ thấy các dòng đã lấy từ view. Prompt không chứa schema của `customer` hay `channel_identity`. Prompt không phải lớp bảo mật.

## Luồng khi khách nhắn

Một lượt chat chỉ gọi model một lần. Timeout backend tới brain là 10 giây, nên không thêm một lượt model chỉ để sinh SQL.

1. Brain chạy đúng một câu `SELECT` đã viết sẵn trong code.
2. Câu đó vẫn đi qua validator, rồi thực thi bằng `chatbot_reader`.
3. Các dòng trả về được gắn vào system prompt.
4. Model viết câu trả lời. `grounding.py` giữ giá khi số tiền khớp catalog vừa đọc, và vẫn xóa tồn kho, khuyến mãi, hoặc giá model tự bịa.

Hàm `run_select` nhận SQL từ bên ngoài cũng đi qua cùng validator và cùng role. Test dùng hàm này để chứng minh câu đụng `customer` bị chặn trước khi tới database.

## Mật khẩu role

Migration tạo role `NOLOGIN`, không ghi mật khẩu vào Git. Backend lúc khởi động đọc `CHATBOT_DB_PASSWORD` và bật `LOGIN`. Brain nối bằng `CHATBOT_DB_URL`. Hai giá trị này chỉ nằm trong `.env`.

Mật khẩu local của demo yếu, cùng kiểu với `POSTGRES_PASSWORD` trong `.env.example`. Máy khác hoặc bản chạy thật phải đổi cả hai biến. Không ghi mật khẩu vào log.
