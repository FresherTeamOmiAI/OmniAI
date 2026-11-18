-- Hồ sơ da của khách (US-19, T-19.3, SRS UC-13). Owner: LucVT.
-- 1 khách 1 hồ sơ, dùng chung mọi kênh đã liên kết. Dữ liệu sức khoẻ nhạy cảm (Luật 91/2025): chỉ ghi khi khách đồng ý
-- (health_consent_at), xoá khách thì xoá hồ sơ. Loại da / vấn đề da dùng lại bảng tra cứu của catalog (V15).

CREATE TABLE skin_profile (
    customer_id       BIGINT      PRIMARY KEY REFERENCES customer (id) ON DELETE CASCADE,
    skin_type         VARCHAR(20) REFERENCES skin_type (code),
    -- Mang thai HOẶC cho con bú: cùng nhóm hoạt chất cần tránh (ai-brain rules/ingredients.py).
    pregnant          BOOLEAN     NOT NULL DEFAULT FALSE,
    -- FORM = khách tự khai trên web, CHAT = AI trích từ hội thoại và khách đã xác nhận (T-19.1).
    source            VARCHAR(10) NOT NULL CHECK (source IN ('FORM', 'CHAT')),
    health_consent_at TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE skin_profile_concern (
    customer_id  BIGINT      NOT NULL REFERENCES skin_profile (customer_id) ON DELETE CASCADE,
    concern_code VARCHAR(30) NOT NULL REFERENCES skin_concern (code),
    PRIMARY KEY (customer_id, concern_code)
);

-- Dị ứng khách tự nêu (chữ tự do, vd. "hương liệu", "cồn"), giữ thứ tự khai, tối đa 10.
-- Bỏ trùng (không phân biệt hoa thường) ở backend, không dùng unique index: Hibernate cập nhật danh sách theo từng
-- vị trí, đổi thứ tự 2 mục sẽ vấp index giữa chừng.
CREATE TABLE skin_profile_allergy (
    customer_id BIGINT      NOT NULL REFERENCES skin_profile (customer_id) ON DELETE CASCADE,
    position    INT         NOT NULL CHECK (position BETWEEN 1 AND 10),
    name        VARCHAR(60) NOT NULL CHECK (length(btrim(name)) > 0),
    PRIMARY KEY (customer_id, position)
);
