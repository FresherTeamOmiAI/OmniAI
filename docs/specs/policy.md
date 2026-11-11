# Policy: luật kênh

> Cập nhật: 2026-11-11 · commit 5bd0986 · Owner: HungDQ
> Nguồn: `shared/policies/*.yaml`, `shared/policies/tests/cases.yaml`, `backend/.../policy/*`, `bench/bench/policy_check.py`

Cùng một bộ YAML được dùng ở **2 nơi**: backend Java (`PolicyEngine`, lọc trước khi gửi khách) và bench Python (`policy_check.py`, chấm lại `finalText`). Đổi định dạng YAML thì phải sửa cả hai.

## Định dạng file YAML

```yaml
channel: SHOPEE          # tên enum ChannelType, hoặc "*" cho luật chung (common.yaml)
display_name: Shopee
owned: false             # kênh shop sở hữu?
max_length: 1000         # cắt câu dài hơn, thêm "…"
rules:
  - id: no-external-link # id ổn định, bench và test tham chiếu theo id này
    description: ...
    pattern: '<regex>'   # không phân biệt hoa thường, Unicode
    action: redact       # block | redact | replace
    replacement: "..."   # chỉ dùng với replace (mặc định "[đã ẩn]")
fallback_message: "..."  # câu thay thế khi block
```

## Nạp file (lúc backend khởi động)
- Đọc mọi `*.yaml` nằm **ngay trong** `shared/policies/` (không đọc thư mục con), theo tên đã sort.
- Gặp lỗi thì backend **không khởi động**. `IllegalStateException` ghi rõ tên file và id luật. Các lỗi được bắt: YAML sai cú pháp hoặc không phải object; `channel` không phải `"*"` hay một giá trị của `ChannelType`; 2 file cùng một kênh; luật thiếu `id`/`pattern`/`action`; action không phải block/redact/replace; regex sai cú pháp; trùng id luật trong một kênh (tính cả luật chung); `max_length` không phải số nguyên dương; kênh có luật `block` mà thiếu `fallback_message`; còn `ChannelType` nào chưa có file.
- `policy_check.py` của bench không kiểm tra các lỗi này. Không cần, vì khi đó backend đã không chạy được.

## Ngữ nghĩa (`PolicyEngine.apply`)
- Thứ tự luật: **luật riêng của kênh trước, luật chung (`*`) sau.** Các file được đọc theo tên đã sort.
- Luật đầu tiên khớp mà là `block`: dừng ngay, trả `fallback_message` với `blocked=true`.
- `redact`: thay mọi đoạn khớp bằng `[đã ẩn]`. `replace`: thay bằng `replacement`.
- Mỗi luật khớp thêm 1 `PolicyViolation(ruleId, action, matched)` (lấy đoạn khớp đầu tiên).
- Áp xong các luật mới cắt theo `max_length`. Độ dài tính theo ký tự Unicode (code point) để không tách đôi emoji; ký tự cuối là `…`.
- Regex Java dùng `CASE_INSENSITIVE | UNICODE_CASE | UNICODE_CHARACTER_CLASS`, Python dùng `re.IGNORECASE`. Viết regex sao cho chạy giống nhau ở cả hai (nhất là `\b` với chữ có dấu).

## Luật hiện có

| File | owned | max_length | Luật |
|---|---|---|---|
| `common.yaml` (`*`) | | | `no-medical-claim` (replace thành "hỗ trợ cải thiện": điều trị, chữa, đặc trị, trị mụn/nám/thâm...) |
| `web.yaml` | true | 2000 | không có luật |
| `shopee.yaml` | false | 1000 | `no-external-link` (redact), `no-phone` (redact), `no-other-channel` (block) |
| `tiktok.yaml` | false | 1000 | như Shopee (danh sách kênh khác thì có Shopee) |

YAML của sàn là **bản nháp**, chưa đối chiếu quy định hiện hành (US-20, S4 sẽ làm và ghi nguồn).

`telegram.yaml` và `messenger.yaml` đã xóa ngày 11/11 cùng 2 kênh đó (decisions #22). Chữ `telegram`, `messenger` vẫn nằm trong regex `no-other-channel` của Shopee và TikTok: bot trên sàn không được nhắc các kênh này.

## Thêm/sửa luật
1. Sửa `shared/policies/<kenh>.yaml`.
2. Thêm ít nhất 2 ca vào `shared/policies/tests/cases.yaml`: 1 ca khớp đúng, 1 ca không được khớp nhầm (ví dụ giá tiền ≠ SĐT, "giá trị" ≠ "trị"). `PolicyEngineTest` và `bench/tests/test_policy_check.py` đều chạy file này trên cả 3 kênh, nên không cần viết test riêng ở mỗi bên. Backend còn kiểm tra thêm cờ `blocked` và việc `finalText` không còn vi phạm.
3. Nếu cần thì thêm ca bench vào `bench/datasets/`.
4. Cập nhật bảng "Luật hiện có" ở trên.

## Lỗ hổng đã biết
- `\bchữa\b` trong `no-medical-claim` khớp cả "sửa chữa", vốn không phải claim y khoa. Để T-20.2 (S4) xử lý cùng các ca không khớp nhầm khác.

## Kế hoạch
- US-20 (S4): đủ luật 3 kênh (WEB, SHOPEE, TIKTOK), mỗi luật có ít nhất 2 ca trong `shared/policies/tests/cases.yaml`.
- US-28 (S6): khi bị block thì gọi brain viết lại câu trả lời (tối đa 1 lần) thay vì dùng câu mẫu.
- US-54 (S7): dùng `PolicyEngine` kiểm nội dung sản phẩm, khuyến mãi trước khi đẩy ra sàn (trụ E), kèm 10 ca bench nội dung.
