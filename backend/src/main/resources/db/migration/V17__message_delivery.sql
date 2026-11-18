-- Trạng thái gửi câu bot ra kênh gửi async (TikTok gửi sau khi đã trả 200 cho webhook). T-25.4. Owner: TuTM.
-- NULL = kênh trả lời đồng bộ (web, simulator API) hoặc tin IN: không theo dõi riêng.
-- RETRYING = gửi lỗi, đang chờ thử lại; SENT = sàn đã nhận; FAILED = hết lượt hoặc lỗi không thử lại được,
-- câu này có handoff = TRUE và nằm trong hàng chờ cho nhân viên (US-35).
ALTER TABLE conversation_message
    ADD COLUMN delivery_status   VARCHAR(10),
    ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN delivery_error    TEXT,
    ADD CONSTRAINT ck_conversation_message_delivery
        CHECK (delivery_status IS NULL OR delivery_status IN ('RETRYING', 'SENT', 'FAILED'));

CREATE INDEX ix_conversation_message_delivery_failed ON conversation_message (created_at)
    WHERE delivery_status = 'FAILED';
