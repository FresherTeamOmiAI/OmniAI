-- Cùng lý do với db/supabase/afterMigrate.sql: khóa Data API của Supabase khỏi bảng simulator.
-- Trên Postgres thường (không có role anon/authenticated) thì không làm gì.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
    END IF;
END $$;
