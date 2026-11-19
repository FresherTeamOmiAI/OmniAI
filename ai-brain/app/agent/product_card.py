"""Gắn thẻ sản phẩm để web hiện ảnh. Owner: ChienPX.

Widget chỉ vẽ ảnh khi câu có dòng "1. Thương hiệu — Tên — 1.290.000đ". Từ T-43.3 brain trả thêm danh sách sản phẩm
của các dòng đó (card_products) để widget không phải tải cả catalog.
"""

import re

from app.rag.search import ProductRow, channel_price, suggestion_lines
from app.schemas import HistoryTurn, SuggestedProduct
from app.tools.price_stock import SKU

_IMAGE = ("ảnh", "hình ảnh", "hình sản phẩm", "xem hình")
_CARD_LINE = re.compile(r"(?m)^\d+\.\s+.+\s—\s+.+\s—\s+\d")
# Cùng mẫu với PRODUCT_LINE ở web/src/features/chat/suggestionCards.tsx.
_CARD_PARTS = re.compile(r"^\d+\.\s+(.+?)\s+—\s+(.+?)\s+—\s+\d{1,3}(?:\.\d{3})*đ$")
MAX_CARD_PRODUCTS = 5
ASK_WHICH = "Bạn gửi giúp shop mã hoặc tên sản phẩm để shop lấy đúng ảnh."
NOT_ON_CHANNEL = "Sản phẩm này hiện chưa bán trên kênh này ạ."


def asks_for_image(text: str) -> bool:
    lowered = text.lower()
    return any(phrase in lowered for phrase in _IMAGE)


def product_named(text: str, rows: list[ProductRow]) -> ProductRow | None:
    folded = text.casefold()
    found = SKU.search(text.upper())
    if found:
        sku = found.group(0)
        for row in rows:
            if row.sku.upper() == sku:
                return row
    named = [row for row in rows if row.name and len(row.name) >= 12 and row.name.casefold() in folded]
    if not named:
        return None
    named.sort(key=lambda row: len(row.name), reverse=True)
    return named[0]


def product_from_history(history: list[HistoryTurn], rows: list[ProductRow]) -> ProductRow | None:
    for turn in reversed(history):
        row = product_named(turn.content, rows)
        if row is not None:
            return row
    return None


def image_reply(text: str, history: list[HistoryTurn], rows: list[ProductRow], channel: str, owned: bool) -> str:
    row = product_named(text, rows) or product_from_history(history, rows)
    if row is None:
        return ASK_WHICH
    lines = suggestion_lines([row], channel, owned)
    if not lines:
        return NOT_ON_CHANNEL
    return f"{lines[0]}\nĐây là ảnh sản phẩm ạ."


def ensure_product_cards(reply: str, rows: list[ProductRow], channel: str, owned: bool) -> str:
    """Thêm dòng thẻ cho sản phẩm được nhắc, để widget hiện ảnh. Không đụng câu đã có thẻ."""
    if "Gợi ý trong shop:" in reply or _CARD_LINE.search(reply):
        return reply
    found: list[ProductRow] = []
    folded = reply.casefold()
    for row in sorted(rows, key=lambda item: len(item.name), reverse=True):
        if not row.name or len(row.name) < 12 or row.name.casefold() not in folded:
            continue
        if any(row.name.casefold() in earlier.name.casefold() for earlier in found):
            continue
        found.append(row)
        if len(found) == 3:
            break
    lines = suggestion_lines(found, channel, owned)
    if not lines:
        return reply
    return "Gợi ý trong shop:\n" + "\n".join(lines) + "\n" + reply


def card_products(reply: str, rows: list[ProductRow], channel: str, owned: bool) -> list[SuggestedProduct]:
    """Sản phẩm của các dòng thẻ trong reply, đúng thứ tự, bỏ trùng, tối đa MAX_CARD_PRODUCTS (T-43.3).

    Đọc lại chính dòng thẻ brain vừa viết nên bắt đủ mọi nguồn (gợi ý, ensure_product_cards, ảnh). Dòng không khớp
    hãng + tên của catalog thì bỏ, không đoán.
    """
    by_identity = {(row.brand.casefold(), row.name.casefold()): row for row in rows}
    found: list[SuggestedProduct] = []
    seen: set[str] = set()
    for raw in reply.splitlines():
        match = _CARD_PARTS.match(raw.strip().replace("**", ""))
        if not match:
            continue
        row = by_identity.get((match.group(1).strip().casefold(), match.group(2).strip().casefold()))
        if row is None or row.sku in seen:
            continue
        price = channel_price(row, channel, owned)
        if price is None:
            continue
        seen.add(row.sku)
        found.append(SuggestedProduct(sku=row.sku, brand=row.brand, name=row.name, price=price, category=row.category))
        if len(found) == MAX_CARD_PRODUCTS:
            break
    return found
