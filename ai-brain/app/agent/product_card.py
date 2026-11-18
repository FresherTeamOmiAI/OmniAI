"""Gắn thẻ sản phẩm để web hiện ảnh. Owner: ChienPX.

Widget chỉ vẽ ảnh khi câu có dòng "1. Thương hiệu — Tên — 1.290.000đ".
"""

import re

from app.rag.search import ProductRow, suggestion_lines
from app.schemas import HistoryTurn
from app.tools.price_stock import SKU

_IMAGE = ("ảnh", "hình ảnh", "hình sản phẩm", "xem hình")
_CARD_LINE = re.compile(r"(?m)^\d+\.\s+.+\s—\s+.+\s—\s+\d")
ASK_WHICH = "Bạn gửi giúp shop mã hoặc tên sản phẩm để shop lấy đúng ảnh."


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
    line = suggestion_lines([row], channel, owned)[0]
    return f"{line}\nĐây là ảnh sản phẩm ạ."


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
    if not found:
        return reply
    lines = suggestion_lines(found, channel, owned)
    return "Gợi ý trong shop:\n" + "\n".join(lines) + "\n" + reply
