"""Điều phối 1 lượt hội thoại. Owner: ChienPX.

Hiện tại: luật thành phần + gọi provider. Sprint 1-3 thay bằng agent (LangGraph) có tool:
search_product, get_price_and_stock, get_customer_profile, get_orders_all_channels,
check_ingredients, get_promotions, create_handoff.
"""

from app.agent.followup import continues_usage, resolve_usage_targets, usage_text
from app.catalog_access import load_public_catalog
from app.llm.base import LLMProvider
from app.rag.search import load_search_rows, parse_constraints, search_product, suggestion_lines
from app.rules.grounding import strip_unverified_numbers
from app.rules.ingredients import check_routine
from app.schemas import ChatRequest, ChatResponse

PREGNANCY_KEYWORDS = ("mang thai", "có bầu", "bầu bí", "cho con bú")


def system_prompt_for(request: ChatRequest, catalog: list[str] | None = None) -> str:
    base = (
        "Bạn là trợ lý tư vấn mỹ phẩm của shop. Khi khách hỏi giá, nêu đúng mức giá trong danh sách catalog. "
        "Không nói tồn kho và không nói khuyến mãi. "
        "Không chẩn đoán bệnh, không dùng từ 'điều trị', 'chữa', 'đặc trị'. "
        "Trả lời bằng văn bản thuần, không markdown, tối đa khoảng 5 câu."
    )
    if catalog:
        base += (
            " Chỉ được nhắc các sản phẩm và đúng các mức giá sau, lấy từ database của shop: "
            + "; ".join(catalog)
            + ". Không nêu sản phẩm, thương hiệu, giá, Lazada hay Fanpage ngoài danh sách này."
        )
    if request.channel_owned:
        return base + " Đây là kênh của shop: được so sánh giá giữa các kênh và gửi link."
    return base + (
        f" Đây là kênh sàn {request.channel}: tuyệt đối không nhắc kênh khác, không gửi link, "
        "không cho/xin số điện thoại."
    )


def respond(request: ChatRequest, provider: LLMProvider) -> ChatResponse:
    pregnant = any(k in request.text.lower() for k in PREGNANCY_KEYWORDS)
    warnings = check_routine(request.text, pregnant=pregnant)

    catalog = load_public_catalog(request.channel, request.channel_owned)
    history = list(request.history)
    if continues_usage(request.text, history):
        rows = load_search_rows() or []
        targets = resolve_usage_targets(request.text, history, rows)
        reply = (
            usage_text(targets)
            if targets
            else "Bạn gửi giúp shop mã hoặc tên sản phẩm vừa xem, shop đọc hướng dẫn dùng cho đúng sản phẩm đó."
        )
        grounding: list[str] = []
    else:
        constraints = parse_constraints(request.text)
        rows = load_search_rows() if constraints.active else None
        hits = (
            search_product(request.text, rows, channel=request.channel, owned=request.channel_owned)
            if rows
            else []
        )
        if constraints.active and rows is not None and not hits:
            reply = "Shop chưa có sản phẩm đang bán khớp yêu cầu này."
            grounding = []
        else:
            lines = suggestion_lines(hits, request.channel, request.channel_owned) if hits else list(catalog.lines)
            reply = provider.generate(request, system_prompt_for(request, lines))
            # Giá chỉ được giữ khi khớp catalog vừa đọc. Tồn kho và khuyến mãi vẫn bị xóa (T-06.6).
            reply, grounding = strip_unverified_numbers(reply, set(catalog.prices))
            if hits:
                reply = "Gợi ý trong shop:\n" + "\n".join(lines) + "\n" + reply
    if warnings:
        reply = "Lưu ý: " + " ".join(w.message for w in warnings) + "\n" + reply

    return ChatResponse(reply=reply, provider=provider.name, warnings=[w.code for w in warnings] + grounding)
