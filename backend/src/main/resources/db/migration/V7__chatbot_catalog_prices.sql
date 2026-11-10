-- Thêm giá niêm yết vào view chatbot. Vẫn không có tồn kho, email, số điện thoại.

CREATE OR REPLACE VIEW chatbot_catalog WITH (security_invoker = false) AS
SELECT brand,
       name,
       category,
       skin_types,
       skin_concerns,
       usage_time,
       price_web,
       price_shopee,
       price_tiktok
FROM product
WHERE active = true;
