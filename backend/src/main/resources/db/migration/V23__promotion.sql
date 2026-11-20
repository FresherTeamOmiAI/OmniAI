-- T-33.1 (US-33, SRS UC-21, decisions #53): khuyến mãi theo kênh và lịch sử giá. Owner: LucVT.
-- Quy ước:
--   * Mỗi khuyến mãi thuộc đúng 1 kênh (channel_code). Bot trên sàn chỉ nói khuyến mãi của chính sàn đó (UC-17).
--   * VOUCHER: mã voucher, giảm số tiền cố định (VND) cho đơn từ min_order_amount, áp cả shop trên kênh đó.
--   * FLASH_SALE: giá sale riêng của từng sản phẩm (promotion_item.sale_price), không có mã.
--   * Mốc giờ là TIMESTAMPTZ; API nhận và trả theo Asia/Ho_Chi_Minh (+07:00).
--   * Trạng thái (sắp diễn ra / đang chạy / hết hạn / tắt) tính lúc đọc từ enabled, starts_at, ends_at:
--     voucher hết hạn tự không được trả, không cần job.
--   * price_history: ProductService phát sự kiện khi giá theo kênh đổi, module promotion ghi 1 dòng cũ -> mới.

CREATE TABLE promotion (
    id               BIGSERIAL    PRIMARY KEY,
    channel_code     VARCHAR(20)  NOT NULL REFERENCES sales_channel (code),
    type             VARCHAR(20)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    voucher_code     VARCHAR(20),
    discount_amount  BIGINT,
    min_order_amount BIGINT,
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_promotion_type CHECK (type IN ('VOUCHER', 'FLASH_SALE')),
    CONSTRAINT ck_promotion_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_promotion_voucher CHECK (
        (type = 'VOUCHER' AND voucher_code IS NOT NULL AND discount_amount > 0 AND min_order_amount >= 0)
        OR (type = 'FLASH_SALE' AND voucher_code IS NULL AND discount_amount IS NULL AND min_order_amount IS NULL)),
    CONSTRAINT uq_promotion_voucher_code UNIQUE (channel_code, voucher_code)
);

CREATE INDEX idx_promotion_channel_ends ON promotion (channel_code, ends_at);

CREATE TABLE promotion_item (
    promotion_id BIGINT NOT NULL REFERENCES promotion (id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    sale_price   BIGINT NOT NULL,
    PRIMARY KEY (promotion_id, product_id),
    CONSTRAINT ck_promotion_item_price CHECK (sale_price > 0)
);

CREATE INDEX idx_promotion_item_product ON promotion_item (product_id);

CREATE TABLE price_history (
    id           BIGSERIAL   PRIMARY KEY,
    product_id   BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    channel_code VARCHAR(20) NOT NULL REFERENCES sales_channel (code),
    old_price    BIGINT,                          -- NULL: giá đầu tiên (mốc lúc chạy migration, hoặc sản phẩm mới tạo)
    new_price    BIGINT      NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_price_history_price CHECK (new_price >= 0 AND (old_price IS NULL OR old_price >= 0))
);

CREATE INDEX idx_price_history_product ON price_history (product_id, channel_code, changed_at DESC);

-- Mốc đầu: giá đang có ở mọi kênh, để lần đổi đầu tiên vẫn biết giá trước đó.
INSERT INTO price_history (product_id, channel_code, old_price, new_price)
SELECT product_id, channel_code, NULL, price FROM product_channel;
