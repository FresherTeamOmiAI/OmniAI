-- T-23.1 (US-23, SRS UC-09, decisions #52): lịch sử đơn mọi kênh theo khách. Owner: LucVT.
-- Quy ước sở hữu đơn:
--   * Đơn WEB thuộc khách ở customer_id (đặt đơn cần đăng nhập từ S3).
--   * Đơn sàn (SHOPEE, TIKTOK; T-51.3 ghi) lưu người mua phía sàn ở buyer_external_id
--     (Shopee buyer_user_id, TikTok user_id) và ĐỂ customer_id NULL. Lúc đọc, đơn sàn thuộc khách C khi
--     channel_identity (channel = channel_code, external_id = buyer_external_id) đang gắn với C.
--     Nhờ vậy liên kết muộn vẫn thấy đơn cũ, gỡ liên kết (US-30) thì đơn ẩn ngay, đơn của danh tính chưa liên kết không lộ.
ALTER TABLE customer_order ADD COLUMN buyer_external_id VARCHAR(255);

ALTER TABLE customer_order ADD CONSTRAINT ck_customer_order_buyer
    CHECK (channel_code <> 'WEB' OR buyer_external_id IS NULL);

CREATE INDEX idx_customer_order_buyer ON customer_order (channel_code, buyer_external_id)
    WHERE buyer_external_id IS NOT NULL;
