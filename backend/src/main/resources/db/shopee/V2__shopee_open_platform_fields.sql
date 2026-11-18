-- Bổ sung field bắt buộc trong tài liệu Open Platform, đọc ngày 2026-11-11. Owner: ChienPX.
-- get_order_detail trả các field "Return by default". Push code 3 có completed_scenario.
-- stock trên update_stock là int64.

ALTER TABLE tbl_shopee_seller_stock
    ALTER COLUMN stock TYPE BIGINT;

ALTER TABLE tbl_shopee_order
    ADD COLUMN cod BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN message_to_seller VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN days_to_ship INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN ship_by_date BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tbl_shopee_order_item
    ADD COLUMN model_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN model_sku VARCHAR(100) NOT NULL DEFAULT '';

ALTER TABLE tbl_shopee_push
    ADD COLUMN completed_scenario VARCHAR(32) NOT NULL DEFAULT '';
