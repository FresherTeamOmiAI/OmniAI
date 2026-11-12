# Catalog sản phẩm thật kèm ảnh (T-47.1)

> Cập nhật: 2026-11-12 · commit ee28494 · Owner: LucVT
> Nguồn: `shared/catalog/products.csv`, `shared/catalog/real/selection.csv`, `tools/catalog/obf_fetch.py`, `tools/catalog/obf_annotate.py`, `tools/catalog/upload_images.py`, `backend/.../db/migration/V9__product_source_image.sql`, `V10__import_catalog_real.sql`
> Liên quan: US-47, [luu-anh-san-pham.md](luu-anh-san-pham.md) (T-47.2), [decisions #26, #27, #30](../specs/decisions.md)

## Kết quả

- **Catalog trong DB là sản phẩm thật** của 50 hãng có bán ở VN, tổng 366 SKU.
  - V9 xoá 80 SKU của 5 thương hiệu hư cấu (decisions #30).
  - V10 nạp `shared/catalog/products.csv`.
- **Theo danh mục:** moisturizer 112, cleanser 83, sunscreen 57, serum 28, lip-care 28, mask 19, toner 15, exfoliant 11, spot-treatment 7, eye-cream 6. Trong đó 80 SKU `manual` (người soạn, đã duyệt ở PR #241) và 286 SKU `auto` (tự soạn theo quy tắc).
- **Ca test:** 364 SKU active, 2 SKU ẩn (`NEUT-SUN-02`, `NIVE-MSK-01`), 4 SKU hết hàng.
- **Hãng nhiều nhất:** Nivea 68, Garnier 30, L'Oréal Paris 26, Neutrogena 26, La Roche-Posay 23, CeraVe 21, Bioderma 19, Cetaphil 15, Avène 13.
- **Nhóm hoạt chất cho luật thành phần của AI Brain:** retinoid 11, AHA 19, BHA 55, vitamin C 46, azelaic 2, **BPO 0**.
- **Ảnh:** mỗi SKU có 1 ảnh WebP (≤ 800 px, ≤ 150 KB; lớn nhất 105 KB, tổng 5,6 MB) ở `shared/catalog/real/images/<SKU>.webp`.
  - Thư mục ảnh nằm trong gitignore, sinh lại bằng `build`.
  - Ảnh được upload lên Supabase Storage. DB lưu `image_key = <SKU>.webp`, backend ghép `imageUrl`.
- **Store** (`/`) hiện ảnh, link "Nguồn" tới trang OBF và dòng ghi nguồn ở cuối trang.

## Nguồn và giấy phép

Dữ liệu lấy từ [Open Beauty Facts](https://world.openbeautyfacts.org) (OBF), dùng dump `en.openbeautyfacts.org.products.csv.gz` (khoảng 20 MB, 80.331 sản phẩm, bản ngày 12/11/2026). Điều khoản đọc ngày 12/11/2026 tại https://world.openbeautyfacts.org/terms-of-use:

| Phần | Giấy phép | Phải làm |
|---|---|---|
| Cơ sở dữ liệu | ODbL; từng nội dung theo DbCL 1.0 | Ghi nguồn. Bộ dữ liệu phái sinh phải chia sẻ cùng giấy phép |
| Ảnh sản phẩm | CC BY-SA 3.0 | Ghi nguồn Open Beauty Facts kèm link (link trang sản phẩm cũng được). Ảnh đã resize vẫn theo CC BY-SA |
| Nhãn hiệu, thiết kế bao bì | Không thuộc giấy phép trên | Thuộc chủ sở hữu. Chỉ dùng cho mục đích học tập (SRS A-05) |

**Giấy phép của bộ dữ liệu này:**
- `shared/catalog/products.csv`, `shared/catalog/real/selection.csv` và bảng `product` là cơ sở dữ liệu phái sinh từ Open Beauty Facts, phát hành theo **Open Database License (ODbL) 1.0** (https://opendatacommons.org/licenses/odbl/1-0/).
- Ảnh trong `shared/catalog/real/images/` và trên Supabase là ảnh của OBF đã resize, phát hành theo **CC BY-SA 3.0** (https://creativecommons.org/licenses/by-sa/3.0/).
- Nguồn: © Open Beauty Facts contributors, https://world.openbeautyfacts.org.

Mỗi SKU có `barcode`, `source_url` (trang sản phẩm trên OBF), `image_source_url` (ảnh gốc) và `image_license_note`.

**Bắt buộc khi hiển thị** (store, trang chi tiết T-09.1, admin, simulator): có dòng ghi nguồn Open Beauty Facts (ODbL, CC BY-SA 3.0) kèm link. `StorePage` đã làm.

## Luồng

```
obf_fetch.py dump       tải dump OBF vào shared/catalog/real/.cache/ (gitignore)
obf_fetch.py discover   lọc dump -> THÊM dòng annotated_by=auto vào selection.csv (không sửa dòng có sẵn)
obf_fetch.py build      selection.csv + dữ liệu OBF -> shared/catalog/products.csv + ảnh WebP
validate_catalog.py     kiểm products.csv
csv_to_sql.py V<n>__... sinh migration upsert theo sku (V10 cho đợt này; lần sau đặt V<n> mới, không sửa V10)
upload_images.py        đẩy ảnh lên Supabase (ghi đè nếu đã có)
```

Cài đặt một lần: `python -m venv tools/catalog/.venv`, rồi `tools/catalog/.venv/Scripts/python -m pip install -r tools/catalog/requirements.txt` (Pillow, chỉ `build` cần).

Lệnh `search <từ khóa>` vẫn còn để tra nhanh qua API (đọc sản phẩm 15 req/phút, tìm 10 req/phút trên mỗi IP). Ngoài lệnh này, mọi bước đều đọc dump, vì OBF yêu cầu dùng dump khi cần hơn vài trăm sản phẩm ([tài liệu API](https://openfoodfacts.github.io/openfoodfacts-server/api/)). Ảnh vẫn tải từng cái, cách nhau ≥ 1 s, gửi User-Agent `OmniAI-catalog/1.0`, có cache.

### `discover`: chọn sản phẩm (`obf_annotate.py`)
- **Hãng:** chỉ hãng trong `BRAND_LIST` (khoảng 70 hãng có bán ở VN, kèm tiền tố SKU và hạng giá). So khớp theo `brands_tags`/`brands_en` sau khi bỏ dấu. Nếu OBF ghi cả công ty mẹ `l-oreal` lẫn hãng con thì lấy hãng con.
- **Danh mục:** dựa vào tên + `categories_tags`, map vào 10 danh mục.
  - Loại tóc, cơ thể, tay, chân, khử mùi, em bé, cạo râu, trang điểm, nước hoa, khăn ướt, xà phòng bánh.
  - Không map được vào danh mục nào thì bỏ.
- **Chất lượng:**
  - Có ảnh và tên; dung tích 3–1000 ml.
  - INCI ≥ 8 thành phần sau `normalize_inci()`, và là INCI tiếng Anh: loại bản dịch tiếng Bồ Đào Nha, Đức, Ý, Thổ và chữ quảng cáo.
  - Bỏ trùng theo (hãng, tên, dung tích), giữ bản có INCI dài nhất.
- **SKU** `TIỀN TỐ-LOẠI-NN`, lấy số tiếp theo còn trống. SKU đã gán không đổi, vì DB upsert theo `sku`.

### Cột nào lấy từ đâu
| Cột | Nguồn |
|---|---|
| `name` | Dòng `manual`: tên tiếng Anh chuẩn do người nhập. Dòng `auto`: tên trên OBF, **có thể là tiếng Pháp/Đức/Thổ**, sửa tay được |
| `brand` | Tên chuẩn theo tiền tố SKU (`BRAND_LIST`) |
| `ingredients_inci` | `ingredients_text` của OBF qua `normalize_inci()`: bỏ tiền tố "Ingredients:" và mã lô, tách cả theo dấu chấm, `1,2-` thành `1.2-`, chữ VIẾT HOA sang Title Case |
| `size_ml` | `quantity` (ml, g, fl oz) hoặc `product_quantity` |
| `pao_months` | `periods_after_opening` của OBF. Thiếu thì lấy giá trị trong `selection.csv` (dòng `auto`: mặc định theo danh mục, retinoid/vitamin C 6 tháng) |
| `barcode`, `source_url`, `image_*` | OBF. Dòng mới tải ảnh bản 400 px; 80 dòng đầu tải bản đầy đủ rồi resize |
| `category`, `skin_types`, `skin_concerns`, `usage_time`, `description` | Dòng `manual`: người soạn. Dòng `auto`: **quy tắc** trong `annotate()`. Loại da theo từ khoá trong tên (oil control → da dầu, sensitive/cica → nhạy cảm…). Vấn đề da theo hoạt chất (salicylic → mụn, lỗ chân lông; niacinamide; hyaluronic → cấp ẩm; retinoid → lão hoá…). Buổi dùng: SPF → sáng; retinoid/AHA/BHA → tối. Mô tả tiếng Việt theo mẫu, không có claim y khoa (validator kiểm) |
| `price_web`, `stock`, `active` | **Shop tự đặt**, không phải giá thật. Dòng `auto`: giá gốc theo danh mục × hạng hãng × dung tích, làm tròn nghìn; tồn kho 20–200 sinh từ hash barcode |
| `price_shopee`, `price_tiktok` | Web + 4% và web − 3%, làm tròn nghìn |

**Sửa dữ liệu:**
- Sửa thẳng dòng tương ứng trong `selection.csv`, đổi `annotated_by` thành `manual` nếu đã duyệt.
- Sau đó chạy `build`, rồi sinh migration **V<n> mới** bằng `csv_to_sql.py`.
- Không sửa tay `products.csv` hay migration đã merge.
- INCI do cộng đồng nhập nên còn lỗi chính tả. Muốn sửa INCI thì sửa trên OBF hoặc đổi barcode.

## Ảnh trên Supabase Storage (decisions #26)

**Tạo project (một lần, người giữ project):**
1. Vào https://supabase.com, đăng ký, chọn **New project** ở gói Free. Region nên chọn Southeast Asia (Singapore).
2. **Storage → New bucket**: tên `product-images`, bật **Public bucket**.
3. **Project Settings → API**: copy **Project URL** và khoá **service_role** (secret), điền vào `.env` ở gốc repo:
   ```
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_BUCKET=product-images
   SUPABASE_SERVICE_KEY=<service_role key>
   ```
   Khoá `service_role` có toàn quyền trên project. **Không commit**, không đưa vào backend hay web. Gửi cho người khác qua kênh riêng của nhóm.
4. Upload ảnh: chạy `python tools/catalog/upload_images.py --check` (thử 1 ảnh), rồi `python tools/catalog/upload_images.py` (tất cả ảnh, ghi đè ảnh đã có). Script mở thử vài URL public để xác nhận bucket đã Public.
5. Máy nào chỉ cần **xem** ảnh thì chỉ cần `SUPABASE_URL` (và `SUPABASE_BUCKET`) trong `.env`. Backend tự ghép `imageUrl`.

**Lưu ý:** project gói Free tự dừng sau 1 tuần không có truy cập. Trước mỗi Sprint Review và trước ngày demo, cần vào dashboard bật lại (xem mục 8 của [luu-anh-san-pham.md](luu-anh-san-pham.md)). Thiếu `SUPABASE_URL` hoặc project đang dừng thì store vẫn chạy, chỉ hiện khung "Chưa có ảnh".

## Việc còn lại
- **Bench, llm-eval, sample ai-brain:** dataset bench, báo cáo llm-eval (`docs/llm-eval/`) và `ai-brain/samples/` còn tham chiếu 5 thương hiệu hư cấu. Cần viết lại dần theo catalog thật (decisions #30). pytest của ai-brain dùng stub nên không hỏng.
- **Store:** phân trang và trang chi tiết (T-09.1), bộ lọc theo danh mục và giá (T-09.2). Hiện `/api/products` trả 20 SKU đầu.
- **Admin:** upload và đổi ảnh từ màn admin (`ProductRequest` chưa có trường ảnh).
- **Dữ liệu:** dịch tên sản phẩm `auto` sang tiếng Anh hoặc Việt; bổ sung hãng Hàn/Nhật (OBF gần như không có).
