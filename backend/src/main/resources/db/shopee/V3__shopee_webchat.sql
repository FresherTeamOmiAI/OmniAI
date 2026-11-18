-- Chat shop. webchat_push code 10 theo snapshot push mechanism (đọc 2026-11-11).
-- send_message / get_message / get_conversation_list là bản giả lập: trang Open Platform trả lỗi không có quyền.

CREATE TABLE tbl_shopee_conversation (
    conversation_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id                BIGINT NOT NULL,
    to_id                  BIGINT NOT NULL,
    to_name                VARCHAR(128) NOT NULL DEFAULT '',
    unread_count           INTEGER NOT NULL DEFAULT 0,
    latest_message_id      VARCHAR(32),
    latest_message_type    VARCHAR(32),
    latest_message_content TEXT,
    latest_message_from_id BIGINT,
    last_message_timestamp BIGINT NOT NULL,
    region                 VARCHAR(8) NOT NULL DEFAULT 'VN',
    CONSTRAINT uq_tbl_shopee_conversation_buyer UNIQUE (shop_id, to_id),
    CONSTRAINT ck_tbl_shopee_conversation_region CHECK (region = 'VN'),
    CONSTRAINT ck_tbl_shopee_conversation_unread CHECK (unread_count >= 0)
);

-- Một dòng là một tin. content là JSON của data.content.content (text / item_id / order_sn).
CREATE TABLE tbl_shopee_message (
    message_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id    BIGINT NOT NULL REFERENCES tbl_shopee_conversation (conversation_id),
    shop_id            BIGINT NOT NULL,
    from_id            BIGINT NOT NULL,
    from_user_name     VARCHAR(128) NOT NULL DEFAULT '',
    to_id              BIGINT NOT NULL,
    to_user_name       VARCHAR(128) NOT NULL DEFAULT '',
    message_type       VARCHAR(32) NOT NULL,
    content            TEXT NOT NULL,
    created_timestamp  BIGINT NOT NULL,
    status             VARCHAR(32) NOT NULL,
    business_type      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_tbl_shopee_message_business CHECK (business_type IN (0, 11))
);

CREATE INDEX idx_tbl_shopee_message_conversation ON tbl_shopee_message (conversation_id, created_timestamp, message_id);

-- Push code 10 của tin khách gửi vào. Tin shop gửi ra bằng send_message không tạo push.
CREATE TABLE tbl_shopee_webchat_push (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES tbl_shopee_message (message_id),
    shop_id         BIGINT NOT NULL,
    code            INTEGER NOT NULL,
    callback_url    TEXT NOT NULL,
    raw_body        TEXT NOT NULL,
    "authorization" VARCHAR(128) NOT NULL,
    "timestamp"     BIGINT NOT NULL,
    CONSTRAINT ck_tbl_shopee_webchat_push_code CHECK (code = 10)
);

CREATE INDEX idx_tbl_shopee_webchat_push_message ON tbl_shopee_webchat_push (message_id);
