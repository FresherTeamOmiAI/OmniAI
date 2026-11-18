"""Vòng agent một lượt: chọn tool rồi dừng. Owner: ChienPX. US-15.

LangGraph giữ vòng chọn tool. Chọn giá/tồn hay tìm sản phẩm là code tất định, vì gọi nhầm tool
thì câu trả lời bịa số. LLM chỉ diễn giải ở các lối không cần số liệu.
"""

from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from app.rag.search import parse_constraints

_STOCK = ("còn hàng", "còn không", "còn mấy", "còn bao nhiêu", "tồn kho", "hết hàng")
_PRICE = ("giá bao nhiêu", "bao nhiêu tiền")


class TurnState(TypedDict, total=False):
    text: str
    tool: str


def route_tool(text: str) -> str:
    """Một trong get_price_and_stock, search_product, none.

    "giá bao nhiêu" là hỏi giá một món. "dưới 300k" là điều kiện tìm, nên vẫn đi search_product.
    """
    lowered = text.lower()
    if any(phrase in lowered for phrase in _STOCK) or any(phrase in lowered for phrase in _PRICE):
        return "get_price_and_stock"
    if parse_constraints(text).active or "gợi ý" in lowered:
        return "search_product"
    return "none"


def _choose(state: TurnState) -> TurnState:
    return {"text": state.get("text", ""), "tool": route_tool(state.get("text", ""))}


_GRAPH = None


def choose_tool(text: str) -> str:
    global _GRAPH
    if _GRAPH is None:
        graph = StateGraph(TurnState)
        graph.add_node("choose", _choose)
        graph.add_edge(START, "choose")
        graph.add_edge("choose", END)
        _GRAPH = graph.compile()
    result = _GRAPH.invoke({"text": text})
    return result["tool"]
