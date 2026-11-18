"""Tool search_product: lọc loại da, giá, nhóm hàng rồi xếp theo embedding. Owner: ChienPX. T-10.2.

Catalog đọc từ view chatbot_catalog_search được nhớ CATALOG_CACHE_TTL_SECONDS giây (mặc định 120) và dùng chung cho
mọi việc của một câu chat: gợi ý, dòng sản phẩm trong prompt, cách dùng, ảnh (US-43, T-43.4).
"""

import logging
import os
import re
import threading
import time
from dataclasses import dataclass, replace

import psycopg

from app.catalog_access import run_select
from app.rag.embed import cosine, embed
from app.rules.sql_guard import SEARCH_PAGE_SIZE, SEARCH_SQL, SqlRejected

log = logging.getLogger(__name__)

SKIN = (
    ("da hỗn hợp", "combination"),
    ("da nhạy cảm", "sensitive"),
    ("da thường", "normal"),
    ("da dầu", "oily"),
    ("da khô", "dry"),
)
# Cụm dài đứng trước để "kem chống nắng" không bị nuốt thành nhóm khác.
CATEGORY = (
    ("kem chống nắng", "sunscreen"),
    ("chống nắng", "sunscreen"),
    ("sữa rửa mặt", "cleanser"),
    ("nước tẩy trang", "cleanser"),
    ("tẩy trang", "cleanser"),
    ("kem dưỡng", "moisturizer"),
    ("son dưỡng", "lip-care"),
    ("mặt nạ", "mask"),
    ("serum", "serum"),
    ("toner", "toner"),
)
_PRICE = re.compile(r"dưới\s*(\d{1,3}(?:[.\s]\d{3})+|\d+)\s*(k|nghìn|ngàn)?", re.IGNORECASE)
# Hỏi rẻ/đắt nhất thì xếp theo giá của kênh, không theo độ gần câu chữ.
_CHEAPEST = ("rẻ nhất", "giá thấp nhất")
_PRICIEST = ("đắt nhất", "mắc nhất", "giá cao nhất")

DEFAULT_CACHE_TTL_S = 120.0
_cache_lock = threading.Lock()
_cache: "tuple[float, tuple[ProductRow, ...]] | None" = None


@dataclass(frozen=True)
class ProductRow:
    sku: str
    brand: str
    name: str
    category: str
    skin_types: str
    skin_concerns: str
    usage_time: str
    # None khi sản phẩm chưa có giá ở kênh đó (V13 cho phép). Không gợi ý sản phẩm ở kênh nó không có giá.
    price_web: int | None
    price_shopee: int | None
    price_tiktok: int | None
    description: str = ""
    active: bool = True
    embedding: tuple[float, ...] | None = None

    def vector(self) -> tuple[float, ...]:
        return self.embedding if self.embedding is not None else embed(product_text(self))


@dataclass(frozen=True)
class CatalogSnapshot:
    """Các dòng sản phẩm đưa cho LLM và đúng các mức giá trong các dòng đó (whitelist của grounding.py)."""

    lines: tuple[str, ...] = ()
    prices: frozenset[int] = frozenset()


@dataclass(frozen=True)
class Constraints:
    skin: str | None = None
    category: str | None = None
    max_price: int | None = None

    @property
    def active(self) -> bool:
        return bool(self.skin or self.category or self.max_price is not None)


def product_text(row: ProductRow) -> str:
    skins = []
    for code in row.skin_types.split(","):
        code = code.strip()
        label = next((phrase for phrase, skin in SKIN if skin == code), "")
        if code:
            skins.append(f"{label} {code}".strip())
    name = row.name
    return " ".join(
        part
        for part in (name, name, row.brand, row.category, " ".join(skins), row.skin_concerns, row.description)
        if part
    )


def parse_constraints(question: str) -> Constraints:
    text = question.lower()
    skin = next((code for phrase, code in SKIN if phrase in text), None)
    category = next((code for phrase, code in CATEGORY if phrase in text), None)
    max_price = None
    match = _PRICE.search(text)
    if match:
        digits = match.group(1).replace(".", "").replace(" ", "")
        amount = int(digits)
        unit = (match.group(2) or "").lower()
        if unit in {"k", "nghìn", "ngàn"} or amount < 1000:
            amount *= 1000
        max_price = amount
    return Constraints(skin, category, max_price)


def channel_price(row: ProductRow, channel: str, owned: bool) -> int | None:
    if owned or channel.upper() == "WEB":
        return row.price_web
    if channel.upper() == "TIKTOK":
        return row.price_tiktok
    return row.price_shopee


def search_product(
    question: str,
    products: list[ProductRow],
    *,
    channel: str = "WEB",
    owned: bool = True,
    limit: int = 3,
) -> list[ProductRow]:
    """Top sản phẩm active có giá ở kênh này, đúng ràng buộc trong câu hỏi, gần câu hỏi nhất.

    Câu hỏi "rẻ nhất" / "đắt nhất" thì xếp theo giá của kênh, cùng giá thì theo độ gần.
    """
    constraints = parse_constraints(question)
    query_vector = embed(question)
    matched = []
    for row in products:
        price = channel_price(row, channel, owned)
        if not row.active or price is None:
            continue
        if constraints.category and row.category != constraints.category:
            continue
        if constraints.skin and constraints.skin not in {part.strip() for part in row.skin_types.split(",")}:
            continue
        if constraints.max_price is not None and price > constraints.max_price:
            continue
        matched.append(row)
    matched.sort(key=lambda row: (-cosine(query_vector, row.vector()), row.name))
    order = _price_order(question)
    if order:
        matched.sort(key=lambda row: order * channel_price(row, channel, owned))
    return matched[:limit]


def _price_order(question: str) -> int:
    text = question.lower()
    if any(phrase in text for phrase in _CHEAPEST):
        return 1
    if any(phrase in text for phrase in _PRICIEST):
        return -1
    return 0


def suggestion_lines(hits: list[ProductRow], channel: str, owned: bool) -> list[str]:
    """Dòng thẻ "1. Hãng — Tên — giá" cho widget. Sản phẩm không có giá ở kênh này bị bỏ."""
    lines = []
    for row in hits:
        amount = channel_price(row, channel, owned)
        if amount is not None:
            lines.append(f"{len(lines) + 1}. {row.brand} — {row.name} — {_vnd(amount)}")
    return lines


def catalog_snapshot(rows: list[ProductRow], channel: str, owned: bool) -> CatalogSnapshot:
    """Dòng sản phẩm cho prompt. Kênh owned thấy giá cả 3 kênh, kênh sàn chỉ thấy giá của sàn đó."""
    lines = []
    prices: set[int] = set()
    for row in rows:
        if owned:
            labelled = (("web", row.price_web), ("Shopee", row.price_shopee), ("TikTok", row.price_tiktok))
            shown = [(label, amount) for label, amount in labelled if amount is not None]
            price_text = "giá " + ", ".join(f"{label} {_vnd(amount)}" for label, amount in shown)
        else:
            amount = channel_price(row, channel, owned)
            shown = [("", amount)] if amount is not None else []
            price_text = f"giá {_vnd(amount)}" if amount is not None else ""
        if not shown:
            continue
        prices.update(amount for _, amount in shown)
        detail = ", ".join(part for part in (row.category, row.skin_types, row.usage_time) if part)
        lines.append(f"{row.brand} — {row.name}" + (f" ({detail})" if detail else "") + f" — {price_text}")
    return CatalogSnapshot(tuple(lines), frozenset(prices))


def search_page_sql(offset: int) -> str:
    """Một trang 100 SKU, thứ tự sku cố định để ghép đủ catalog."""
    if offset < 0 or offset % SEARCH_PAGE_SIZE != 0:
        raise ValueError("offset trang search phải là bội số của 100")
    if offset == 0:
        return SEARCH_SQL
    return f"{SEARCH_SQL} OFFSET {offset}"


def cache_ttl() -> float:
    raw = os.getenv("CATALOG_CACHE_TTL_SECONDS", "").strip()
    try:
        return max(0.0, float(raw)) if raw else DEFAULT_CACHE_TTL_S
    except ValueError:
        log.warning("CATALOG_CACHE_TTL_SECONDS không phải số, dùng %.0f", DEFAULT_CACHE_TTL_S)
        return DEFAULT_CACHE_TTL_S


def clear_cache() -> None:
    global _cache
    with _cache_lock:
        _cache = None


def load_search_rows() -> list[ProductRow] | None:
    """Mọi SKU active của view search, nhớ cache_ttl() giây. None khi không đọc được database (lỗi không được nhớ).

    Có khoá: FastAPI chạy /chat trong threadpool, các câu chat tới cùng lúc khi cache hết hạn chờ đúng một lần đọc.
    """
    global _cache
    with _cache_lock:
        if _cache is not None and time.monotonic() - _cache[0] < cache_ttl():
            return list(_cache[1])
        loaded = _read_all_rows()
        if loaded is not None:
            _cache = (time.monotonic(), tuple(loaded))
        return loaded


def _read_all_rows() -> list[ProductRow] | None:
    loaded: list[ProductRow] = []
    offset = 0
    while offset <= SEARCH_PAGE_SIZE * 20:
        try:
            rows = run_select(search_page_sql(offset))
        except (SqlRejected, psycopg.Error, OSError) as error:
            log.warning("Không đọc được chatbot_catalog_search: %s", error.__class__.__name__)
            return None
        loaded.extend(_row_from_record(record) for record in rows)
        if len(rows) < SEARCH_PAGE_SIZE:
            return loaded
        offset += SEARCH_PAGE_SIZE
    log.warning("chatbot_catalog_search dài hơn số trang cho phép")
    return None


def optional_price(value) -> int | None:
    return None if value is None else int(value)


def _row_from_record(record: tuple) -> ProductRow:
    sku, brand, name, category, skin_types, concerns, usage_time, web, shopee, tiktok, description, raw_vector = record
    row = ProductRow(
        sku=sku,
        brand=brand,
        name=name,
        category=category,
        skin_types=skin_types or "",
        skin_concerns=concerns or "",
        usage_time=usage_time or "",
        price_web=optional_price(web),
        price_shopee=optional_price(shopee),
        price_tiktok=optional_price(tiktok),
        description=description or "",
        embedding=_parse_vector(raw_vector),
    )
    # Vector có sẵn trong DB thì parse 1 lần lúc nạp; sản phẩm chưa index thì nhúng 1 lần ở đây, không mỗi câu chat.
    return row if row.embedding is not None else replace(row, embedding=embed(product_text(row)))


def _parse_vector(raw: str | None) -> tuple[float, ...] | None:
    if not raw:
        return None
    body = raw.strip().strip("[]")
    if not body:
        return None
    return tuple(float(part) for part in body.split(","))


def _vnd(amount: int) -> str:
    return f"{amount:,}".replace(",", ".") + "đ"
