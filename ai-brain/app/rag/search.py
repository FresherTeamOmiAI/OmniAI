"""Tool search_product: lọc loại da, giá, nhóm hàng rồi xếp theo embedding. Owner: ChienPX. T-10.2."""

import logging
import re
from dataclasses import dataclass

import psycopg

from app.catalog_access import run_select
from app.rag.embed import cosine, embed
from app.rules.sql_guard import SEARCH_SQL, SqlRejected

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


@dataclass(frozen=True)
class ProductRow:
    sku: str
    brand: str
    name: str
    category: str
    skin_types: str
    skin_concerns: str
    usage_time: str
    price_web: int
    price_shopee: int
    price_tiktok: int
    description: str = ""
    active: bool = True
    embedding: tuple[float, ...] | None = None

    def vector(self) -> tuple[float, ...]:
        return self.embedding if self.embedding is not None else embed(product_text(self))


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


def channel_price(row: ProductRow, channel: str, owned: bool) -> int:
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
    """Top sản phẩm active, đúng ràng buộc trong câu hỏi, gần câu hỏi nhất."""
    constraints = parse_constraints(question)
    query_vector = embed(question)
    matched = []
    for row in products:
        if not row.active:
            continue
        if constraints.category and row.category != constraints.category:
            continue
        if constraints.skin and constraints.skin not in {part.strip() for part in row.skin_types.split(",")}:
            continue
        if constraints.max_price is not None and channel_price(row, channel, owned) > constraints.max_price:
            continue
        matched.append(row)
    matched.sort(key=lambda row: (-cosine(query_vector, row.vector()), row.name))
    return matched[:limit]


def suggestion_lines(hits: list[ProductRow], channel: str, owned: bool) -> list[str]:
    lines = []
    for index, row in enumerate(hits, start=1):
        amount = channel_price(row, channel, owned)
        lines.append(f"{index}. {row.brand} — {row.name} — {_vnd(amount)}")
    return lines


def load_search_rows() -> list[ProductRow] | None:
    """Đọc view search. None khi không nối được database, để chat vẫn dùng catalog đầy đủ."""
    try:
        rows = run_select(SEARCH_SQL)
    except (SqlRejected, psycopg.Error, OSError) as error:
        log.warning("Không đọc được chatbot_catalog_search: %s", error.__class__.__name__)
        return None
    loaded = []
    for sku, brand, name, category, skin_types, concerns, usage_time, web, shopee, tiktok, description, raw_vector in rows:
        loaded.append(
            ProductRow(
                sku=sku,
                brand=brand,
                name=name,
                category=category,
                skin_types=skin_types or "",
                skin_concerns=concerns or "",
                usage_time=usage_time or "",
                price_web=int(web),
                price_shopee=int(shopee),
                price_tiktok=int(tiktok),
                description=description or "",
                embedding=_parse_vector(raw_vector),
            )
        )
    return loaded


def _parse_vector(raw: str | None) -> tuple[float, ...] | None:
    if not raw:
        return None
    body = raw.strip().strip("[]")
    if not body:
        return None
    return tuple(float(part) for part in body.split(","))


def _vnd(amount: int) -> str:
    return f"{amount:,}".replace(",", ".") + "đ"
