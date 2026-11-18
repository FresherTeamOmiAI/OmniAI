-- Đồng bộ giá và tồn kho riêng với nội dung sản phẩm (US-50, T-50.2). Owner: TuTM.
-- 1 sản phẩm x 1 kênh có tối đa 3 dòng: PRODUCT (nội dung, giữ external_id), PRICE (giá của kênh đó), STOCK (tồn).
-- Đổi giá Shopee chỉ làm dòng PRICE/SHOPEE chờ gửi; đổi tồn làm dòng STOCK của mọi kênh chờ gửi.
-- V16 để trống cho PR #273 (T-13.1).
ALTER TABLE product_channel_sync ADD COLUMN aspect VARCHAR(10) NOT NULL DEFAULT 'PRODUCT';
ALTER TABLE product_channel_sync ADD CONSTRAINT ck_product_channel_sync_aspect
    CHECK (aspect IN ('PRODUCT', 'PRICE', 'STOCK'));
ALTER TABLE product_channel_sync DROP CONSTRAINT product_channel_sync_pkey;
ALTER TABLE product_channel_sync ADD PRIMARY KEY (product_id, channel_code, aspect);
