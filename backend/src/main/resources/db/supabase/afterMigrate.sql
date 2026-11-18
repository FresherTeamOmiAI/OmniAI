-- Flyway callback, chỉ nạp ở profile "supabase" (application-supabase.yml, decisions #33). Owner: LucVT.
-- Supabase mở Data API (PostgREST) cho role anon/authenticated và mặc định cấp quyền trên mọi bảng mới ở schema public.
-- OmniAI không dùng Data API: backend và ai-brain nối thẳng Postgres. Thu hết quyền để anon key (công khai)
-- không đọc/ghi được customer, conversation, channel_credential... Chạy lại sau mỗi lần migrate nên bảng mới cũng được khoá.
-- Trên Postgres thường (không có 2 role này) thì không làm gì.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
    END IF;
END $$;
