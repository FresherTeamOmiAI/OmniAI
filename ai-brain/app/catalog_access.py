"""Đọc catalog bằng role chatbot_reader. Lỗi kết nối thì trả danh sách rỗng, chat vẫn chạy."""

import logging
import os
from dataclasses import dataclass

import psycopg

from app.rules.sql_guard import CATALOG_SQL, SqlRejected, validate_sql

log = logging.getLogger(__name__)


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


def run_select(sql: str) -> list[tuple]:
    """Validate rồi thực thi bằng CHATBOT_DB_URL. Role này không đọc được bảng khách."""
    checked = validate_sql(sql)
    url = os.getenv("CHATBOT_DB_URL", "").strip()
    if not url:
        raise psycopg.OperationalError("thiếu CHATBOT_DB_URL")
    with psycopg.connect(url, connect_timeout=2) as connection:
        connection.read_only = True
        with connection.cursor() as cursor:
            cursor.execute("SET statement_timeout = '2s'")
            cursor.execute(checked)
            return cursor.fetchall()
