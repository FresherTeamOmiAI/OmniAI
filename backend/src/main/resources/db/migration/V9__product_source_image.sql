-- Catalog thật (T-47.1, US-47). Owner: LucVT.
-- 1) Cột nguồn và ảnh: mỗi sản phẩm 1 ảnh trên Supabase Storage, DB chỉ lưu khoá ảnh (decisions #26).
--    Cho phép NULL vì sản phẩm admin tạo bằng API chưa có ảnh.
-- 2) Xoá 80 SKU của 5 thương hiệu hư cấu (V2, V5). PO cho xoá hẳn dữ liệu giả để thay bằng dữ liệu thật
--    (decisions #30). G-07 "không xoá cứng" vẫn áp dụng cho admin; đây là dọn dữ liệu seed, không phải thao tác admin.
--    Không có bảng nào tham chiếu product (V1-V7), nên xoá an toàn. Catalog thật nạp ở V10.

ALTER TABLE product
    ADD COLUMN barcode            VARCHAR(32),
    ADD COLUMN source_url         VARCHAR(500),
    ADD COLUMN image_key          VARCHAR(128),
    ADD COLUMN image_source_url   VARCHAR(500),
    ADD COLUMN image_license_note VARCHAR(300);

ALTER TABLE product ADD CONSTRAINT product_barcode_key UNIQUE (barcode);

-- Lọc theo cả tiền tố SKU để không phụ thuộc cách chuẩn hoá Unicode của tên hãng có dấu.
DELETE FROM product
WHERE split_part(sku, '-', 1) IN ('LUMI', 'SEN', 'HYD', 'MOC', 'VAN')
   OR brand IN ('Lumi Lab', 'Sen Việt', 'Hydra Mint', 'Mộc Nhiên', 'Vân Khê');
