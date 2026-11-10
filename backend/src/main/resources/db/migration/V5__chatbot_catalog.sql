-- Catalog công khai cho chatbot. Không gồm giá, tồn kho, và không đụng bảng khách.
-- security_invoker = false: chatbot_reader đọc view bằng quyền owner, không cần SELECT trên product.

CREATE VIEW chatbot_catalog WITH (security_invoker = false) AS
SELECT brand, name, category, skin_types, skin_concerns, usage_time
FROM product
WHERE active = true;

REVOKE ALL ON TABLE chatbot_catalog FROM PUBLIC;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'chatbot_reader') THEN
        CREATE ROLE chatbot_reader NOLOGIN;
    END IF;
END $$;

REVOKE ALL ON SCHEMA public FROM chatbot_reader;
GRANT USAGE ON SCHEMA public TO chatbot_reader;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM chatbot_reader;
GRANT SELECT ON TABLE chatbot_catalog TO chatbot_reader;
