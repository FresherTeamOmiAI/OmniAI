"""Câu hỏi tiếp theo trong cùng cuộc chat. Không mở lượt tìm sản phẩm mới khi khách đang hỏi cách dùng."""

import re

from app.rag.search import ProductRow
from app.schemas import HistoryTurn

_SKU = re.compile(r"\b[A-Z]{2,5}-[A-Z]{2,5}-\d{2}\b", re.IGNORECASE)
_USAGE = (
    "cách sử dụng",
    "cách dùng",
    "dùng thế nào",
    "dùng như thế nào",
    "dùng sao",
    "hướng dẫn dùng",
    "hướng dẫn sử dụng",
    "cách thoa",
    "thoa thế nào",
)
_GROUP = ("bọn này", "mấy cái", "mấy sản phẩm", "chúng nó", "cả mấy", "cả ba", "cả 3")
_SINGULAR = ("của nó", "nó ấy", "cái đó", "cái này", "sản phẩm đó", "sản phẩm này")
_NEW_SEARCH = ("gợi ý", "tìm ", "loại khác", "cho da ")
_WHEN = {"am": "buổi sáng", "pm": "buổi tối", "am,pm": "sáng và tối"}


def asks_usage(text: str) -> bool:
    low = text.lower()
    return any(phrase in low for phrase in _USAGE)


def continues_usage(text: str, history: list[HistoryTurn]) -> bool:
    """Khách vừa hỏi cách dùng, rồi gửi tên hoặc mã sản phẩm thay vì hỏi món mới."""
    if asks_usage(text):
        return True
    previous = [turn.content for turn in history if turn.role.lower() == "user"]
    if not previous or not asks_usage(previous[-1]):
        return False
    low = text.lower()
    if any(phrase in low for phrase in _NEW_SEARCH):
        return False
    return "dưới" not in low


def resolve_usage_targets(text: str, history: list[HistoryTurn], rows: list[ProductRow]) -> list[ProductRow]:
    named = _products_named(text, rows)
    low = text.lower()
    group = any(phrase in low for phrase in _GROUP)
    if named and not group:
        return named
    focused = _latest_focus(history, rows)
    if group or any(phrase in low for phrase in _SINGULAR) or asks_usage(text):
        return focused or named
    return named


def usage_text(targets: list[ProductRow]) -> str:
    blocks = []
    for row in targets:
        when = _WHEN.get(row.usage_time, "")
        header = f"{row.sku} — {row.brand} — {row.name}"
        if when:
            header += f" (dùng {when})"
        body = row.description.strip() or "Shop chưa có hướng dẫn chi tiết cho sản phẩm này."
        blocks.append(f"{header}\n{body}")
    if len(blocks) == 1:
        return "Cách dùng:\n" + blocks[0]
    return "Cách dùng các sản phẩm vừa gợi ý:\n\n" + "\n\n".join(blocks)


def _products_named(text: str, rows: list[ProductRow]) -> list[ProductRow]:
    by_sku = {row.sku.upper(): row for row in rows}
    skus = []
    for match in _SKU.findall(text):
        row = by_sku.get(match.upper())
        if row and row not in skus:
            skus.append(row)
    if skus:
        return skus
    low = text.lower()
    found = [row for row in rows if row.name.lower() in low]
    found.sort(key=lambda row: low.find(row.name.lower()))
    return found


def _latest_focus(history: list[HistoryTurn], rows: list[ProductRow]) -> list[ProductRow]:
    for turn in reversed(history):
        named = _products_named(turn.content, rows)
        if named:
            return named
    return []
