# Policy: luật kênh

> Cập nhật: 2026-11-03 · commit c88bfc6 · Owner: HungDQ
> Nguồn: `shared/policies/*.yaml`, `backend/.../policy/*`, `bench/bench/policy_check.py`

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

## Ngữ nghĩa (`PolicyEngine.apply`)
- Thứ tự luật: **luật riêng của kênh trước, luật chung (`*`) sau.** Các file được đọc theo tên đã sort.
- Luật đầu tiên khớp mà là `block`: dừng ngay, trả `fallback_message` với `blocked=true`.
- `redact`: thay mọi đoạn khớp bằng `[đã ẩn]`. `replace`: thay bằng `replacement`.
- Mỗi luật khớp thêm 1 `PolicyViolation(ruleId, action, matched)` (lấy đoạn khớp đầu tiên).
- Áp xong các luật mới cắt theo `max_length`.
- Regex Java dùng `CASE_INSENSITIVE | UNICODE_CASE | UNICODE_CHARACTER_CLASS`, Python dùng `re.IGNORECASE`. Viết regex sao cho chạy giống nhau ở cả hai (nhất là `\b` với chữ có dấu).

## Luật hiện có

| File | owned | max_length | Luật |
|---|---|---|---|
| `common.yaml` (`*`) | | | `no-medical-claim` (replace thành "hỗ trợ cải thiện": điều trị, chữa, đặc trị, trị mụn/nám/thâm...) |
| `web.yaml` | true | 2000 | không có luật |
| `telegram.yaml` | true | 4000 | không có luật |
| `messenger.yaml` | true | 2000 | không có luật |
| `shopee.yaml` | false | 1000 | `no-external-link` (redact), `no-phone` (redact), `no-other-channel` (block) |
| `tiktok.yaml` | false | 1000 | như Shopee (danh sách kênh khác thì có Shopee) |

YAML của sàn là **bản nháp**, chưa đối chiếu quy định hiện hành (US-20, S4 sẽ làm và ghi nguồn).

## Thêm/sửa luật
1. Sửa `shared/policies/<kenh>.yaml`.
2. Thêm test vào `backend/src/test/.../PolicyEngineTest.java` **và** `bench/tests/test_policy_check.py`: cần cả ca khớp đúng và ca không khớp nhầm (giá tiền ≠ SĐT, "giá trị" ≠ "trị").
3. Nếu cần thì thêm ca bench vào `bench/datasets/`.
4. Cập nhật bảng "Luật hiện có" ở trên.

## Kế hoạch
- US-20 (S4): đủ luật 4 kênh, mỗi luật có ít nhất 2 test.
- US-28 (S6): khi bị block thì gọi brain viết lại câu trả lời (tối đa 1 lần) thay vì dùng câu mẫu.
