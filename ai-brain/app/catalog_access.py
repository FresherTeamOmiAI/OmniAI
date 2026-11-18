"""Đọc catalog bằng role chatbot_reader.

Thiếu CHATBOT_DB_URL thì brain không khởi động (require_config). Lỗi kết nối lúc đang chạy thì trả danh sách rỗng,
chat vẫn chạy và grounding.py bỏ mọi giá vì không có giá nào để đối chiếu.
"""

import logging
import os
import threading
import time
from dataclasses import dataclass

import psycopg

from app.rules.sql_guard import CATALOG_SQL, SqlRejected, validate_sql

log = logging.getLogger(__name__)
# Catalog 366 SKU ít đổi trong lúc demo. Nhớ 2 phút để mỗi câu chat không bắt tay Supabase lại từ đầu.
_CACHE_TTL_S = 120.0
_lock = threading.Lock()
_connection: psycopg.Connection | None = None
_cache: dict[str, tuple[float, list[tuple]]] = {}


@dataclass(frozen=True)
class CatalogSnapshot:
    lines: tuple[str, ...] = ()
    prices: frozenset[int] = frozenset()


def _vnd(amount: int) -> str:
    return f"{amount:,}".replace(",", ".") + "đ"


def _prices_for_channel(channel: str, owned: bool, web: int, shopee: int, tiktok: int) -> tuple[str, set[int]]:
    if owned:
        text = f"giá web {_vnd(web)}, Shopee {_vnd(shopee)}, TikTok {_vnd(tiktok)}"
        return text, {web, shopee, tiktok}
    if channel.upper() == "TIKTOK":
        return f"giá {_vnd(tiktok)}", {tiktok}
    return f"giá {_vnd(shopee)}", {shopee}


def require_config() -> None:
    """Gọi lúc khởi động. Brain luôn đọc catalog từ Postgres thật, không có chế độ chạy thiếu database (decisions #25)."""
    if not os.getenv("CHATBOT_DB_URL", "").strip():
        raise ValueError("Thiếu CHATBOT_DB_URL: brain đọc catalog từ Postgres bằng role chatbot_reader (xem .env.example)")


def load_public_catalog(channel: str = "WEB", channel_owned: bool = True) -> CatalogSnapshot:
    """Sản phẩm đang bán và giá được phép nêu, lấy qua câu SELECT cố định đã được validate."""
    try:
        rows = run_select(CATALOG_SQL)
    except (SqlRejected, psycopg.Error, OSError) as error:
        log.warning("Không đọc được chatbot_catalog: %s", error.__class__.__name__)
        return CatalogSnapshot()
    lines = []
    prices: set[int] = set()
    for brand, name, category, skin_types, _concerns, usage_time, web, shopee, tiktok in rows:
        detail = ", ".join(part for part in (category, skin_types, usage_time) if part)
        price_text, allowed = _prices_for_channel(channel, channel_owned, int(web), int(shopee), int(tiktok))
        prices.update(allowed)
        label = f"{brand} — {name}" + (f" ({detail})" if detail else "") + f" — {price_text}"
        lines.append(label)
    return CatalogSnapshot(tuple(lines), frozenset(prices))


def _reader() -> psycopg.Connection:
    global _connection
    if _connection is not None and not _connection.closed:
        return _connection
    url = os.getenv("CHATBOT_DB_URL", "").strip()
    if not url:
        raise psycopg.OperationalError("thiếu CHATBOT_DB_URL")
    _connection = psycopg.connect(url, connect_timeout=3)
    _connection.read_only = True
    return _connection


def _drop_reader() -> None:
    global _connection
    if _connection is not None:
        try:
            _connection.close()
        except psycopg.Error:
            pass
    _connection = None


def run_select(sql: str) -> list[tuple]:
    """Validate rồi thực thi bằng CHATBOT_DB_URL. Role này không đọc được bảng khách."""
    checked = validate_sql(sql)
    now = time.monotonic()
    with _lock:
        cached = _cache.get(checked)
        if cached is not None and now - cached[0] < _CACHE_TTL_S:
            return cached[1]
        try:
            connection = _reader()
            with connection.cursor() as cursor:
                cursor.execute("SET statement_timeout = '2s'")
                cursor.execute(checked)
                rows = cursor.fetchall()
            connection.rollback()
        except psycopg.Error:
            _drop_reader()
            raise
        _cache[checked] = (time.monotonic(), rows)
        return rows
