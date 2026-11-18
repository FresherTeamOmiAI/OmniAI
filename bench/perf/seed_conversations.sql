-- Seed 200 hội thoại × 50 tin = 10.000 tin nhắn cho load test US-43 (AC: "300 SKU, 10.000 tin nhắn").
-- CHỈ chạy trên DB local. Chạy lại được: xoá dữ liệu perf cũ (external_id 'perf-%') trước khi tạo.
--   docker compose exec -T postgres psql -U omniai -d omniai < bench/perf/seed_conversations.sql
-- Dọn sau khi đo: DELETE FROM conversation WHERE external_id LIKE 'perf-%';  (tin nhắn xoá theo ON DELETE CASCADE)

BEGIN;

DELETE FROM conversation WHERE external_id LIKE 'perf-%';

INSERT INTO conversation (channel, external_id, channel_user_id, started_at, last_message_at, message_count)
SELECT (ARRAY['WEB', 'SHOPEE', 'TIKTOK'])[1 + c % 3],
       'perf-' || c,
       'perf-user-' || c,
       now() - make_interval(days => c % 30, mins => c),
       now() - make_interval(days => c % 30),
       50
FROM generate_series(1, 200) AS c;

INSERT INTO conversation_message (conversation_id, direction, message_id, text, raw_text, created_at)
SELECT conv.id,
       CASE WHEN m % 2 = 0 THEN 'IN' ELSE 'OUT' END,
       'perf-' || conv.id || '-' || m,
       CASE WHEN m % 2 = 0 THEN 'Da dầu thì dùng kem chống nắng nào dưới 300k ạ?'
            ELSE 'Dạ, bạn có thể tham khảo kem chống nắng dạng gel, kiềm dầu tốt ạ.' END,
       CASE WHEN m % 2 = 0 THEN NULL ELSE 'Dạ, bạn có thể tham khảo kem chống nắng dạng gel, kiềm dầu tốt ạ.' END,
       conv.started_at + make_interval(secs => m * 30)
FROM conversation AS conv
CROSS JOIN generate_series(1, 50) AS m
WHERE conv.external_id LIKE 'perf-%';

COMMIT;

SELECT count(*) AS perf_conversations,
       (SELECT count(*) FROM conversation_message msg JOIN conversation c ON c.id = msg.conversation_id
        WHERE c.external_id LIKE 'perf-%') AS perf_messages
FROM conversation WHERE external_id LIKE 'perf-%';
