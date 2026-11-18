"""Tool get_price_and_stock: giá theo kênh và tồn kho lấy từ API backend. Owner: ChienPX. US-15.

Không đọc cột stock trên view chatbot. Số trong câu trả lời là số API trả về.
"""

import os
import re
from dataclasses import dataclass

import httpx

from app.rag.search import ProductRow, _vnd

SKU = re.compile(r"\b[A-Z]{2,8}-[A-Z]{2,8}-\d{2}\b")
ASK_WHICH = "Bạn gửi giúp shop mã hoặc tên sản phẩm để shop xem đúng giá và tồn."
UNAVAILABLE = "Shop chưa kiểm tra được giá và tồn lúc này. Bạn gửi lại mã sản phẩm giúp shop nhé."


@dataclass(frozen=True)
class PriceStock:
    sku: str
    brand: str
    name: str
    category: str
    prices: dict[str, int]
    stock: int

    def price_for(self, channel: str, owned: bool) -> int | None:
        code = "WEB" if owned or channel.upper() == "WEB" else channel.upper()
        amount = self.prices.get(code)
        return None if amount is None else int(amount)


def resolve_sku(question: str, rows: list[ProductRow]) -> str | None:
    found = SKU.search(question.upper())
    if found:
        return found.group(0)
    folded = question.casefold()
    named = [row for row in rows if row.name and row.name.casefold() in folded]
    if not named:
        return None
    named.sort(key=lambda row: len(row.name), reverse=True)
    return named[0].sku


def fetch_price_stock(sku: str, client: httpx.Client | None = None) -> PriceStock | None:
    """None khi SKU không có hoặc backend không trả lời."""
    base = os.getenv("BACKEND_URL", "http://localhost:8080").rstrip("/")
    url = f"{base}/api/products/sku/{sku}"
    try:
        response = client.get(url) if client is not None else httpx.get(url, timeout=2.0)
    except httpx.HTTPError:
        raise LookupError(sku) from None
    if response.status_code == 404:
        return None
    if response.status_code != 200:
        raise LookupError(sku)
    body = response.json()
    return PriceStock(
        sku=body["sku"],
        brand=body.get("brand") or "",
        name=body.get("name") or "",
        category=body.get("category") or "",
        prices={str(key): int(value) for key, value in (body.get("prices") or {}).items()},
        stock=int(body.get("stock") or 0),
    )


def format_price_stock(item: PriceStock, channel: str, owned: bool, substitute: PriceStock | None = None) -> str:
    price = item.price_for(channel, owned)
    price_text = _vnd(price) if price is not None else "chưa có giá trên kênh này"
    label = f"{item.brand} — {item.name}".strip(" —")
    if item.stock <= 0:
        text = f"{label} hiện hết hàng. Giá niêm yết {price_text}."
        if substitute is not None and substitute.stock > 0:
            other = substitute.price_for(channel, owned)
            other_text = _vnd(other) if other is not None else "chưa có giá trên kênh này"
            text += f" Shop còn {substitute.brand} — {substitute.name}, giá {other_text}, còn {substitute.stock}."
        return text
    return f"{label}: giá {price_text}, còn {item.stock}."


def answer_price_and_stock(
    question: str,
    rows: list[ProductRow],
    *,
    channel: str,
    owned: bool,
    client: httpx.Client | None = None,
) -> str:
    sku = resolve_sku(question, rows)
    if sku is None:
        return ASK_WHICH
    try:
        item = fetch_price_stock(sku, client)
    except LookupError:
        return UNAVAILABLE
    if item is None:
        return "Shop không thấy sản phẩm đang bán với mã này."
    substitute = None
    if item.stock <= 0:
        substitute = _in_stock_alternative(item, rows, channel, owned, client)
    return format_price_stock(item, channel, owned, substitute)


def _in_stock_alternative(
    item: PriceStock,
    rows: list[ProductRow],
    channel: str,
    owned: bool,
    client: httpx.Client | None,
) -> PriceStock | None:
    checked = 0
    for row in rows:
        if row.sku == item.sku or row.category != item.category:
            continue
        checked += 1
        if checked > 5:
            break
        try:
            other = fetch_price_stock(row.sku, client)
        except LookupError:
            return None
        if other is not None and other.stock > 0 and other.price_for(channel, owned) is not None:
            return other
    return None
