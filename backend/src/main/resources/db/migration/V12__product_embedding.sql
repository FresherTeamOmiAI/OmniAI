-- Embedding catalog cho RAG (T-10.1, US-10). Owner: ChienPX.
-- V8 trên dev là channel_credential, V9-V11 là ảnh, catalog thật và hội thoại.
-- Chỉ sản phẩm active đi vào view. Không thêm tồn kho, email, số điện thoại.

ALTER TABLE product
    ADD COLUMN embedding vector(768);

CREATE INDEX idx_product_embedding_cosine
    ON product USING hnsw (embedding vector_cosine_ops);

CREATE VIEW chatbot_catalog_search WITH (security_invoker = false) AS
SELECT sku,
       brand,
       name,
       category,
       skin_types,
       skin_concerns,
       usage_time,
       price_web,
       price_shopee,
       price_tiktok,
       embedding,
       description
FROM product
WHERE active = true;

REVOKE ALL ON TABLE chatbot_catalog_search FROM PUBLIC;
GRANT SELECT ON TABLE chatbot_catalog_search TO chatbot_reader;
