# Lưu ảnh sản phẩm: Supabase Storage hay local (T-47.2)

> Cập nhật: 2026-11-12 · commit 0821a35 · Người làm: LucVT · Người chốt: HungDQ (PO)
> Trả lời SRS Q-10, AC của US-47. Liên quan: [decisions #13, #22](../specs/decisions.md), [data-model.md](../specs/data-model.md)

## 1. Câu hỏi cần PO chốt

1. Ảnh sản phẩm lưu ở **local (trong repo và thư mục trên máy)** hay ở **Supabase Storage**?
2. Mỗi sản phẩm có **1 ảnh** hay **nhiều ảnh**? AC của US-47 chỉ yêu cầu "ít nhất 1 ảnh mở được".

**Đề xuất:** chọn **local**, lưu nhiều ảnh mỗi sản phẩm (bảng riêng). DB chỉ lưu khoá ảnh để sau này đổi sang Supabase mà không phải migrate dữ liệu. Lý do ở mục 5.

## 2. Ảnh được dùng ở đâu

| Nơi dùng | Sprint | Cần gì |
|---|---|---|
| Web store: danh sách, chi tiết (T-09.1) | S2 | URL ảnh mở được trên trình duyệt |
| Admin thêm/sửa sản phẩm (US-08 đã có màn) | S2–S3 | Upload ảnh lúc chạy |
| Đẩy sản phẩm ra simulator Shopee (US-49), TikTok Shop (US-50) | S3–S4 | Simulator đọc được ảnh từ hub. Cách sàn thật nhận ảnh xem bảng đối chiếu ở T-21.4 và T-25.3 |
| Backend test (Testcontainers), CI (US-16) | S2+ | Chạy không cần mạng và không cần khóa |
| 4 máy dev, máy demo (01/02/2027) | cả dự án | Cùng một bộ ảnh, ổn định tới ngày demo |
| AI Brain, RAG (US-10) | S2 | **Không** dùng ảnh |

Ràng buộc chung: chi phí 0đ (#13). Dữ liệu và ảnh thật phải ghi nguồn và điều kiện sử dụng (#22). Backlog có rủi ro "bản quyền ảnh, nhãn hiệu của bên thứ ba".

## 3. Dữ kiện về Supabase Storage (gói Free)

Lấy ngày 12/11/2026 từ tài liệu chính thức:

| Mục | Giá trị | Nguồn |
|---|---|---|
| Dung lượng storage | 1 GB | [pricing](https://supabase.com/pricing), [billing-on-free-plan](https://supabase.com/docs/guides/platform/billing-on-free-plan) |
| Egress | 5 GB egress + 5 GB cached egress | [pricing](https://supabase.com/pricing) |
| File tối đa | 50 MB, gói Free không nâng được | [file-limits](https://supabase.com/docs/guides/storage/uploads/file-limits) |
| Project bị tạm dừng | "Free projects are paused after 1 week of inactivity". Tối đa 2 project đang chạy | [pricing](https://supabase.com/pricing) |
| Resize ảnh (Image Transformations) | Không có ở gói Free, chỉ từ Pro | [image-transformations](https://supabase.com/docs/guides/storage/serving/image-transformations) |
| URL ảnh public | `https://[project_id].supabase.co/storage/v1/object/public/[bucket]/[asset-name]`, ai có link cũng mở được, có CDN (Free là "Basic CDN") | [downloads](https://supabase.com/docs/guides/storage/serving/downloads), [cdn](https://supabase.com/docs/guides/storage/cdn/fundamentals) |
| Bucket private | Chỉ mở bằng signed URL hoặc header Authorization | [downloads](https://supabase.com/docs/guides/storage/serving/downloads) |
| Khi vượt hạn mức gói Free | Tài liệu không ghi rõ | |

Chưa xác minh: đường dẫn REST để upload từ Spring Boot. Trang API reference trả 404. Supabase không có SDK Java chính thức, nên phải tự gọi HTTP bằng service key. Khoá này không được commit.

Ước lượng dung lượng: khoảng 80 SKU × 2 ảnh, WebP, cạnh dài 800 px, ≤ 150 KB mỗi ảnh, tổng ≤ 25 MB. Cả hai phương án đều dư sức chứa. Vì gói Free không resize ảnh, cả hai phương án đều phải resize trước khi lưu (làm trong script của T-47.1).

## 4. Hai phương án

**A. Local**
- Ảnh gốc của catalog commit vào repo: `shared/catalog/images/<SKU>/<n>.webp`. Cách này giống CSV đang làm: nạp bằng migration, mọi máy có cùng bộ ảnh (#18).
- Ảnh admin upload lúc chạy ghi vào một thư mục trên máy, cấu hình bằng biến môi trường, đưa vào gitignore.
- Backend phục vụ ảnh qua đường tĩnh `/images/**`. Web và simulator gọi qua đường này.

**B. Supabase Storage**
- Một public bucket. Backend upload bằng service key (biến môi trường), DB lưu URL public.
- Ảnh catalog phải upload lên bucket một lần bằng script.

**Đã loại:** để thẳng link ảnh gốc của nhà bán (hotlink). Lý do: link có thể chết hoặc bị chặn hotlink, ảnh nằm ngoài tầm kiểm soát của nhóm, rủi ro bản quyền cao nhất.

## 5. Chấm điểm

Điểm 0–1, cao là tốt. Chi phí 0đ là cổng bắt buộc, cả A và B đều qua.

| Tiêu chí | Trọng số | A. Local | B. Supabase |
|---|---|---|---|
| Chạy offline, trong Testcontainers và CI | 25% | 1: không cần mạng, không cần khóa | 0,3: cần mạng và khóa, test phải giả lập Storage |
| Ổn định tới ngày demo | 20% | 1 | 0,5: project tự dừng sau 1 tuần không dùng (ví dụ nghỉ Tết), phải nhớ bật lại |
| Bản quyền ảnh của bên thứ ba | 15% | 1: ảnh chỉ mở ở localhost | 0,3: bucket public, ai có link cũng xem được |
| Công tích hợp | 15% | 1: đường tĩnh + ghi file, khoảng 0,5 md | 0,5: HTTP client, khóa, script upload, giả lập trong test, khoảng 1,5 md |
| Ảnh admin upload dùng chung giữa các máy | 15% | 0: chỉ nằm trên máy đã upload | 1 |
| Giống hệ thống thật (URL có CDN) | 10% | 0,5 | 1 |
| **Tổng** | | **0,80** | **0,55** |

Cách tính tổng: A = 0,25 + 0,20 + 0,15 + 0,15 + 0 + 0,05 = 0,80. B = 0,075 + 0,10 + 0,045 + 0,075 + 0,15 + 0,10 = 0,545, làm tròn 0,55.

**Đề xuất: A (local).** Điểm yếu duy nhất của A là ảnh upload lúc chạy không dùng chung giữa các máy. Với demo thì điều này chấp nhận được, vì ảnh catalog đã nằm trong repo và demo chạy trên một máy.

**Đường lùi:** DB chỉ lưu khoá ảnh, ví dụ `LUMI-SUN-01/1.webp`, không lưu URL đầy đủ. Backend dựng URL qua một interface lưu ảnh, mặc định là bản local. Nếu sau này cần Supabase, chỉ cần thêm một bản cài đặt và đổi cấu hình, không phải migrate dữ liệu.

## 6. Hệ quả cho các task sau

Chỉ là định hướng, sẽ làm ở task tương ứng:
- **Migration mới** (không sửa V1–V7): bảng `product_image` gồm `product_id`, `image_key`, `position`, `source_url`, `license_note`. Nguồn và điều kiện sử dụng ghi theo từng ảnh, đúng AC của US-47. Nếu PO chọn 1 ảnh thì thêm cột vào `product`, nhưng vẫn cần chỗ ghi nguồn.
- **API và web:** `Product` có thêm danh sách ảnh (URL do backend dựng), cập nhật `web/src/shared/types.ts`, `api-contracts.md`.
- **T-47.1:** script tải ảnh về, resize sang WebP 800 px, đặt tên theo `<SKU>/<n>.webp`, ghi nguồn vào CSV.
- **US-49, US-50:** simulator lấy ảnh qua `/images/**` của hub. Định dạng gửi đi theo bảng đối chiếu API sàn.

## 7. Rủi ro

- Ảnh thật có bản quyền và nhãn hiệu. Chỉ dùng cho demo nội bộ, ghi nguồn từng ảnh, ưu tiên nguồn có giấy phép rõ (T-47.1).
- Repo lớn thêm khoảng 25 MB. Chặn bằng giới hạn kích thước trong script resize.
- Ảnh admin upload trên máy demo sẽ mất nếu xoá thư mục. Chấp nhận được cho demo, ghi vào `tools/systembat/README.md` khi làm.
