-- Tách giá theo kênh ra khỏi bảng product (T-04.1 sửa theo review, decisions #32). Owner: LucVT.
-- Trước: 3 cột price_web, price_shopee, price_tiktok, nên thêm 1 sàn là phải ALTER bảng product, entity, API, form.
-- Sau: bảng sales_channel (danh sách kênh) + product_channel (1 dòng / sản phẩm / kênh).
--      Thêm sàn mới = INSERT 1 dòng sales_channel + INSERT giá, không đổi cấu trúc bảng nào.
-- "Đủ giá cho mọi kênh active" (AC US-04) không ép được bằng ràng buộc thường: ProductService kiểm, test bảo vệ.

CREATE TABLE sales_channel (
    code   VARCHAR(20)  PRIMARY KEY,              -- khớp enum ChannelType của backend
    name   VARCHAR(100) NOT NULL,
    owned  BOOLEAN      NOT NULL DEFAULT FALSE,   -- kênh shop sở hữu (SRS G-15): được so sánh giá, gửi link
    active BOOLEAN      NOT NULL DEFAULT TRUE,    -- false = tạm ngừng bán trên kênh, giữ nguyên dữ liệu giá
    CONSTRAINT ck_sales_channel_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

INSERT INTO sales_channel (code, name, owned, active) VALUES
    ('WEB', 'Website OmniAI', TRUE, TRUE),
    ('SHOPEE', 'Shopee', FALSE, TRUE),
    ('TIKTOK', 'TikTok Shop', FALSE, TRUE);

-- Thông tin của 1 sản phẩm trên 1 kênh. Hiện chỉ có giá niêm yết; mã sản phẩm phía sàn, trạng thái đồng bộ
-- (US-49) sẽ thêm cột vào bảng này. Khuyến mãi để bảng riêng (US-33).
CREATE TABLE product_channel (
    product_id   BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    channel_code VARCHAR(20) NOT NULL REFERENCES sales_channel (code),
    price        BIGINT      NOT NULL,            -- VND; lần sửa cuối xem product.updated_at
    PRIMARY KEY (product_id, channel_code),
    CONSTRAINT ck_product_channel_price CHECK (price >= 0)
);

CREATE INDEX idx_product_channel_channel ON product_channel (channel_code);

-- Chép giá hiện có của mọi sản phẩm (366 SKU x 3 kênh).
INSERT INTO product_channel (product_id, channel_code, price)
SELECT id, 'WEB', price_web FROM product
UNION ALL
SELECT id, 'SHOPEE', price_shopee FROM product
UNION ALL
SELECT id, 'TIKTOK', price_tiktok FROM product;

-- 2 view của chatbot (V7, V12) đọc 3 cột giá: xoá rồi tạo lại với ĐÚNG tên cột cũ để ai-brain không phải đổi.
-- Thêm kênh mới thì chỉ cần CREATE OR REPLACE VIEW thêm cột, không đụng dữ liệu.
DROP VIEW chatbot_catalog;
DROP VIEW chatbot_catalog_search;

ALTER TABLE product
    DROP CONSTRAINT ck_product_prices,
    DROP COLUMN price_web,
    DROP COLUMN price_shopee,
    DROP COLUMN price_tiktok;

-- Giá theo kênh ở dạng cột, cho 2 view chatbot. NULL nếu sản phẩm chưa có giá ở kênh đó.
CREATE VIEW product_price_pivot AS
SELECT product_id,
       MAX(price) FILTER (WHERE channel_code = 'WEB')    AS price_web,
       MAX(price) FILTER (WHERE channel_code = 'SHOPEE') AS price_shopee,
       MAX(price) FILTER (WHERE channel_code = 'TIKTOK') AS price_tiktok
FROM product_channel
GROUP BY product_id;

REVOKE ALL ON TABLE product_price_pivot FROM PUBLIC;

CREATE VIEW chatbot_catalog WITH (security_invoker = false) AS
SELECT p.brand,
       p.name,
       p.category,
       p.skin_types,
       p.skin_concerns,
       p.usage_time,
       pp.price_web,
       pp.price_shopee,
       pp.price_tiktok
FROM product p
JOIN product_price_pivot pp ON pp.product_id = p.id
WHERE p.active = true;

CREATE VIEW chatbot_catalog_search WITH (security_invoker = false) AS
SELECT p.sku,
       p.brand,
       p.name,
       p.category,
       p.skin_types,
       p.skin_concerns,
       p.usage_time,
       pp.price_web,
       pp.price_shopee,
       pp.price_tiktok,
       p.embedding,
       p.description
FROM product p
JOIN product_price_pivot pp ON pp.product_id = p.id
WHERE p.active = true;

REVOKE ALL ON TABLE chatbot_catalog FROM PUBLIC;
REVOKE ALL ON TABLE chatbot_catalog_search FROM PUBLIC;
GRANT SELECT ON TABLE chatbot_catalog TO chatbot_reader;
GRANT SELECT ON TABLE chatbot_catalog_search TO chatbot_reader;
