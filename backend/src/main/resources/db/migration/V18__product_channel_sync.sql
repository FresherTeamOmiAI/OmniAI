-- Đồng bộ sản phẩm chiều đi hub -> sàn (US-49, T-49.1, decisions #42). Owner: TuTM.
-- 1 dòng / sản phẩm / kênh: vừa là hàng đợi (outbox) vừa là trạng thái hiện trên admin (T-49.3).
-- Không thêm cột vào product_channel vì bảng đó là @ElementCollection của Product: Hibernate xoá rồi ghi lại khi
-- đổi giá, sẽ làm mất trạng thái đồng bộ.
CREATE TABLE product_channel_sync (
    product_id      BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    channel_code    VARCHAR(20)  NOT NULL REFERENCES sales_channel (code),
    -- PENDING: chờ gửi (mới đổi hoặc đang chờ thử lại); SYNCED: sàn đã nhận bản mới nhất; FAILED: hết lượt hoặc
    -- lỗi không thử lại được, admin bấm thử lại
    status          VARCHAR(10)  NOT NULL,
    -- SHA-256 của nội dung cần có trên sàn (bản mới nhất) và của bản đã gửi thành công gần nhất.
    -- Bằng nhau thì sự kiện mới là trùng, không gửi lại.
    content_hash    VARCHAR(64)  NOT NULL,
    synced_hash     VARCHAR(64),
    -- Mã sản phẩm phía sàn (Shopee item_id, TikTok product_id) sau lần tạo đầu tiên
    external_id     VARCHAR(64),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error      TEXT,
    synced_at       TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (product_id, channel_code),
    CONSTRAINT ck_product_channel_sync_status CHECK (status IN ('PENDING', 'SYNCED', 'FAILED')),
    CONSTRAINT ck_product_channel_sync_attempts CHECK (attempts >= 0)
);

-- Worker chỉ quét dòng PENDING đến hạn.
CREATE INDEX ix_product_channel_sync_due ON product_channel_sync (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_product_channel_sync_status ON product_channel_sync (status, channel_code);
