"""Đọc catalog bằng role chatbot_reader.

Thiếu CHATBOT_DB_URL thì brain không khởi động (require_config). Lỗi kết nối lúc đang chạy thì trả danh sách rỗng,
chat vẫn chạy và grounding.py bỏ mọi giá vì không có giá nào để đối chiếu.

Một kết nối dùng lại, có khoá. Catalog đã đọc được nhớ ở rag/search.py (load_search_rows), nên database chỉ bị đọc
khi cache hết hạn và không cần pool (decisions #45). Mở kết nối lỗi thì ngừng thử CONNECT_BACKOFF_S giây: mỗi lần
thử với mật khẩu sai là một lần đăng nhập hỏng, đủ nhiều thì pooler Supabase chặn cả role (ECIRCUITBREAKER, 18/11).
"""

import logging
import os
import threading
import time

import psycopg

from app.rules.sql_guard import validate_sql

log = logging.getLogger(__name__)
CONNECT_BACKOFF_S = 60.0
_lock = threading.Lock()
_connection: psycopg.Connection | None = None
_blocked_until = 0.0


class CatalogUnavailable(psycopg.OperationalError):
    """Đang trong thời gian ngừng thử sau một lần mở kết nối lỗi. Không chạm tới database."""


def require_config() -> None:
    """Gọi lúc khởi động. Brain luôn đọc catalog từ Postgres thật, không có chế độ chạy thiếu database (decisions #25)."""
    if not os.getenv("CHATBOT_DB_URL", "").strip():
        raise ValueError("Thiếu CHATBOT_DB_URL: brain đọc catalog từ Postgres bằng role chatbot_reader (xem .env.example)")


def _reason(error: psycopg.Error) -> str:
    """Tóm tắt lỗi mở kết nối mà không in thông báo gốc (có host, user)."""
    text = str(error).lower()
    if "circuit" in text:
        return "pooler đang chặn role (ECIRCUITBREAKER)"
    if "password" in text or "authentication" in text:
        return "sai user hoặc mật khẩu"
    if "timeout" in text or "timed out" in text:
        return "hết thời gian chờ"
    return "không kết nối được"


def _reader() -> psycopg.Connection:
    global _connection, _blocked_until
    if _connection is not None and not _connection.closed:
        return _connection
    if time.monotonic() < _blocked_until:
        raise CatalogUnavailable("đang ngừng thử kết nối catalog")
    url = os.getenv("CHATBOT_DB_URL", "").strip()
    if not url:
        raise psycopg.OperationalError("thiếu CHATBOT_DB_URL")
    try:
        # autocommit: transaction do run_select tự mở (BEGIN READ ONLY) trong cùng 1 lần gửi.
        _connection = psycopg.connect(url, connect_timeout=3, autocommit=True)
    except psycopg.Error as error:
        _blocked_until = time.monotonic() + CONNECT_BACKOFF_S
        log.warning(
            "Không mở được kết nối chatbot_reader: %s (%s, sqlstate=%s). Ngừng thử %.0f s",
            _reason(error),
            error.__class__.__name__,
            error.sqlstate or "-",
            CONNECT_BACKOFF_S,
        )
        raise
    return _connection


def _drop_reader() -> None:
    global _connection
    if _connection is not None:
        try:
            _connection.close()
        except psycopg.Error:
            pass
    _connection = None


def reset() -> None:
    """Đóng kết nối và bỏ thời gian ngừng thử. Dùng trong pytest và script đo."""
    global _blocked_until
    with _lock:
        _drop_reader()
        _blocked_until = 0.0


def run_select(sql: str) -> list[tuple]:
    """Validate rồi thực thi bằng CHATBOT_DB_URL. Role này không đọc được bảng khách.

    Transaction chỉ đọc, timeout 2 s, gửi 1 lần (simple query nhiều câu, không tham số): tới pooler Supabase mỗi vòng
    mạng ~300 ms, gửi SET, SELECT, ROLLBACK riêng thì 1 trang mất ~0,9 s thay vì ~0,3 s (đo 18/11).
    """
    checked = validate_sql(sql)
    with _lock:
        try:
            connection = _reader()
            with connection.cursor() as cursor:
                cursor.execute(f"BEGIN READ ONLY; SET LOCAL statement_timeout = '2s'; {checked}; COMMIT")
                rows: list[tuple] = []
                while True:
                    if cursor.description is not None:
                        rows = cursor.fetchall()
                    if not cursor.nextset():
                        break
        except CatalogUnavailable:
            raise
        except psycopg.Error:
            _drop_reader()
            raise
        return rows
