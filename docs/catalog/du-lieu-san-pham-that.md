# Dữ liệu sản phẩm thật kèm ảnh (T-47.1)

> Cập nhật: 2026-11-12 · commit 1324b0f · Owner: LucVT
> Nguồn: `shared/catalog/real/selection.csv`, `shared/catalog/products_real.csv`, `tools/catalog/obf_fetch.py`
> Liên quan: US-47, [luu-anh-san-pham.md](luu-anh-san-pham.md) (T-47.2), [decisions #26, #27](../specs/decisions.md)

## Kết quả

- `shared/catalog/products_real.csv`: 80 sản phẩm thật của 29 hãng có bán ở VN (CeraVe, La Roche-Posay, Bioderma, The Ordinary, Cetaphil, Avène, Neutrogena, Garnier, L'Oréal Paris, COSRX…).
  - Có đủ 10 danh mục: cleanser 12, toner 8, serum 14, moisturizer 13, sunscreen 10, exfoliant 4, mask 5, spot-treatment 5, eye-cream 4, lip-care 5.
  - Giữ 4 SKU hết hàng (`stock=0`) và 2 SKU ẩn (`active=false`) để test, giống CSV cũ.
- Mỗi sản phẩm có tên, hãng, giá, INCI, 1 ảnh, link nguồn và ghi chú giấy phép, đúng AC của US-47.
- Ảnh đã resize nằm ở `shared/catalog/real/images/<SKU>.webp`: WebP, cạnh dài ≤ 800 px, ≤ 150 KB (lớn nhất 105 KB, tổng 2,1 MB).
  - Thư mục ảnh **không commit** (gitignore), chạy `build` để sinh lại.
  - Khi làm phần ảnh, script sẽ upload thư mục này lên Supabase (decisions #26).
- **Chưa thay** `products.csv` và DB. Xem mục "Việc còn lại".

## Nguồn và giấy phép

Lấy từ [Open Beauty Facts](https://world.openbeautyfacts.org) (OBF), cơ sở dữ liệu mỹ phẩm mở do cộng đồng đóng góp. Điều khoản đọc ngày 12/11/2026 tại https://world.openbeautyfacts.org/terms-of-use:

| Phần | Giấy phép | Phải làm |
|---|---|---|
| Cơ sở dữ liệu | ODbL; từng nội dung theo DbCL 1.0 | Ghi nguồn. Bộ dữ liệu phái sinh (như `products_real.csv`) phải chia sẻ cùng giấy phép |
| Ảnh sản phẩm | CC BY-SA 3.0 | Ghi nguồn Open Beauty Facts kèm link (link trang sản phẩm cũng được). Ảnh đã resize vẫn theo CC BY-SA |
| Nhãn hiệu, thiết kế bao bì | Không thuộc giấy phép trên | Thuộc chủ sở hữu. Chỉ dùng cho mục đích học tập (SRS A-05) |

**Giấy phép của bộ dữ liệu này:**
- `shared/catalog/products_real.csv` và `shared/catalog/real/selection.csv` là cơ sở dữ liệu phái sinh từ Open Beauty Facts, phát hành theo **Open Database License (ODbL) 1.0** (https://opendatacommons.org/licenses/odbl/1-0/).
- Các ảnh trong `shared/catalog/real/images/` và trên Supabase là ảnh của OBF đã resize, phát hành theo **CC BY-SA 3.0** (https://creativecommons.org/licenses/by-sa/3.0/).
- Nguồn: © Open Beauty Facts contributors, https://world.openbeautyfacts.org.

Mỗi dòng trong `products_real.csv` có `source_url` (trang sản phẩm trên OBF), `image_source_url` (ảnh gốc) và `image_license_note`.

**Bắt buộc khi hiển thị** (web store T-09.1, admin, simulator): có dòng "Dữ liệu và ảnh: Open Beauty Facts (ODbL, CC BY-SA 3.0)" kèm link tới `source_url` của sản phẩm.

## Cột nào lấy từ đâu

| Cột | Nguồn |
|---|---|
| `name` | Cột `name` trong `selection.csv` (tên tiếng Anh chuẩn của sản phẩm). Bỏ trống thì lấy `product_name` của OBF, ưu tiên bản vi > en > mặc định > fr |
| `brand` | Tên hãng chuẩn theo tiền tố SKU (`BRANDS` trong script), vì OBF ghi lẫn "Cerave"/"CeraVe" và có bản ghi để trống hãng |
| `ingredients_inci` | `ingredients_text_en` hoặc `ingredients_text` của OBF, qua `normalize_inci()` |
| `size_ml` | `product_quantity`, hoặc tách số từ `quantity`. Sản phẩm tính bằng gram được ghi như ml |
| `pao_months` | `periods_after_opening` của OBF. Đa số sản phẩm không có, khi đó lấy giá trị trong `selection.csv` |
| `category`, `skin_types`, `skin_concerns`, `usage_time`, `description` | **Shop tự soạn** trong `selection.csv`, dựa trên công dụng ghi trên sản phẩm. Mô tả viết lại bằng tiếng Việt, không chép từ hãng, không có claim y khoa |
| `price_web`, `stock`, `active` | **Shop tự đặt** (demo), theo mức giá thị trường. Không phải giá của nguồn |
| `price_shopee`, `price_tiktok` | Shopee ≈ web + 4%, TikTok ≈ web − 3%, làm tròn tới nghìn đồng, giống CSV cũ |

`normalize_inci()` xử lý như sau:
- Bỏ tiền tố "Ingredients:", mã lô in trên bao bì, dấu `*`.
- Tách thành phần theo dấu phẩy, dấu chấm phẩy và cả dấu chấm (có nhãn dùng dấu chấm làm dấu phân cách).
- Đổi `1,2-Hexanediol` thành `1.2-Hexanediol`, vì cột INCI tách theo dấu phẩy.
- Đổi chuỗi VIẾT HOA sang Title Case, giữ nguyên các chữ viết tắt (PEG, PPG, BHT…).

Mỗi sản phẩm đã chọn có ≥ 8 thành phần. Theo các nhóm hoạt chất mà luật thành phần của AI Brain nhận biết: retinoid 4, AHA 9, BHA 14, vitamin C 6, azelaic 1, **BPO 0**.

**Lưu ý chất lượng:** INCI do cộng đồng nhập hoặc đọc từ ảnh, nên còn vài lỗi chính tả hay thiếu dấu phẩy, ví dụ `Sodium Hyaluro - Nate`. Đã loại 4 sản phẩm có INCI bằng tiếng Bồ Đào Nha, tiếng Đức hoặc chỉ là chữ quảng cáo. Khi sửa thì sửa ở OBF, hoặc thay bằng barcode khác, **không** sửa tay trong `products_real.csv`.

## Cách chạy

```
python -m venv tools/catalog/.venv
tools/catalog/.venv/Scripts/python -m pip install -r tools/catalog/requirements.txt   # Pillow
cd tools/catalog
.venv/Scripts/python obf_fetch.py search "la roche posay"   # in ứng viên: barcode, số thành phần INCI, có ảnh, dung tích, PAO
.venv/Scripts/python obf_fetch.py build                     # selection.csv -> products_real.csv + ảnh
python validate_catalog.py ../../shared/catalog/products_real.csv
```

- Muốn thêm hoặc đổi sản phẩm: sửa `shared/catalog/real/selection.csv`, rồi chạy `build`.
  - `build` báo `BỎ <SKU>` nếu sản phẩm thiếu tên, hãng, INCI (< 5 thành phần), dung tích, PAO hoặc ảnh.
- Giới hạn API của OBF: đọc sản phẩm 15 req/phút, tìm kiếm 10 req/phút trên mỗi IP. Vượt có thể bị chặn IP ([tài liệu API](https://openfoodfacts.github.io/openfoodfacts-server/api/)).
  - Script tự giãn khoảng cách giữa các request và gửi User-Agent `OmniAI-catalog/1.0`.
  - JSON và ảnh gốc được cache trong `shared/catalog/real/.cache/` (gitignore), nên chạy lại `build` không gọi API nữa.
  - Lần đầu mất khoảng 8 phút cho 80 sản phẩm.
- `validate_catalog.py` nhận cả header cũ (`products.csv`) lẫn header có thêm 5 cột nguồn/ảnh, và kiểm thêm barcode, `image_key = <SKU>.webp`, URL https, ghi chú giấy phép.

## Việc còn lại (ngoài T-47.1)

Thay catalog hư cấu bằng `products_real.csv` sẽ ảnh hưởng tới nhiều người. Cần hẹn thời điểm với HungDQ và ChienPX trước khi làm:
- **Migration mới:** upsert 80 SKU thật, thêm 3 cột ảnh vào `product` (decisions #26), ẩn 80 SKU hư cấu bằng `active=false` (G-07, không xoá).
  - `csv_to_sql.py` hiện chỉ đọc các cột của `HEADER`, nên cần sửa để đọc thêm cột ảnh.
- **Test:** `OmniAiApplicationTests` đang đếm 78 SKU active và kiểm `chatbot_catalog`.
- **Bench, llm-eval, embedding:** dataset bench và báo cáo llm-eval tham chiếu SKU hư cấu (decisions #19). ChienPX đang embed catalog cho RAG (US-10).
- **Ảnh:** upload `shared/catalog/real/images/` lên Supabase Storage. Backend ghép URL ảnh, web hiển thị ảnh cùng dòng ghi nguồn.
