-- Schema catalog đợt 1 (T-04.1, US-04). Owner: LucVT.
-- Thêm trường cho RAG (US-10) và routine (US-31); bắt buộc các trường mà AC US-04 yêu cầu; chặn số âm.
-- Cột embedding (pgvector) do US-10 thêm ở migration riêng.

ALTER TABLE product
    ADD COLUMN description   VARCHAR(2000),
    ADD COLUMN skin_concerns VARCHAR(255),
    ADD COLUMN usage_time    VARCHAR(10),
    ADD COLUMN updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

-- AC US-04: mỗi SKU phải có tên, giá theo 3 kênh, INCI, loại da, dung tích, PAO; thương hiệu.
ALTER TABLE product
    ALTER COLUMN brand            SET NOT NULL,
    ALTER COLUMN category         SET NOT NULL,
    ALTER COLUMN skin_types       SET NOT NULL,
    ALTER COLUMN ingredients_inci SET NOT NULL,
    ALTER COLUMN size_ml          SET NOT NULL,
    ALTER COLUMN pao_months       SET NOT NULL,
    ALTER COLUMN price_web        SET NOT NULL,
    ALTER COLUMN price_shopee     SET NOT NULL,
    ALTER COLUMN price_tiktok     SET NOT NULL;

ALTER TABLE product
    ADD CONSTRAINT ck_product_prices CHECK (price_web >= 0 AND price_shopee >= 0 AND price_tiktok >= 0),
    ADD CONSTRAINT ck_product_stock CHECK (stock >= 0),
    ADD CONSTRAINT ck_product_size CHECK (size_ml > 0),
    ADD CONSTRAINT ck_product_pao CHECK (pao_months > 0),
    ADD CONSTRAINT ck_product_usage_time CHECK (usage_time IN ('am', 'pm', 'am,pm'));

CREATE INDEX idx_product_category ON product (category);
