"""Điều phối 1 lượt hội thoại. Owner: ChienPX.

Vòng LangGraph chọn tool giá/tồn hoặc tìm sản phẩm. Các tool còn lại (hồ sơ, đơn, khuyến mãi, handoff) chưa có.
"""

import logging
import time

from app.agent.followup import continues_usage, resolve_usage_targets, usage_text
from app.agent.loop import choose_tool
from app.agent.product_card import asks_for_image, ensure_product_cards, image_reply
from app.llm.base import LLMProvider
from app.rag.search import catalog_snapshot, load_search_rows, parse_constraints, search_product, suggestion_lines
from app.rules.grounding import strip_unverified_numbers
from app.rules.ingredients import check_routine
from app.schemas import ChatRequest, ChatResponse
from app.tools.price_stock import answer_price_and_stock

log = logging.getLogger(__name__)
PREGNANCY_KEYWORDS = ("mang thai", "có bầu", "bầu bí", "cho con bú")
# Câu không có gợi ý: prompt chỉ nhận chừng này sản phẩm gần câu hỏi nhất, không phải cả catalog (US-43).
PROMPT_PRODUCTS = 8


def system_prompt_for(request: ChatRequest, catalog: list[str] | None = None) -> str:
    base = (
        "Bạn là trợ lý tư vấn mỹ phẩm của shop. Khi khách hỏi giá hoặc còn hàng, chỉ nói số do tool get_price_and_stock trả về. "
        "Không nói khuyến mãi. Không nói là kênh không gửi được ảnh. "
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
        "không cho/xin số điện thoại. Không viết http, www, Zalo, TikTok, Lazada, Tiki, "
        "website hay trang web. Câu trả lời dưới 1000 ký tự."
    )


def _ms_since(started: float) -> float:
    return (time.perf_counter() - started) * 1000


def respond(request: ChatRequest, provider: LLMProvider) -> ChatResponse:
    pregnant = any(k in request.text.lower() for k in PREGNANCY_KEYWORDS)
    warnings = check_routine(request.text, pregnant=pregnant)

    started = time.perf_counter()
    loaded = load_search_rows()
    db_ms = _ms_since(started)
    rank_ms = llm_ms = 0.0
    history = list(request.history)
    rows = loaded or []
    attach_cards = True
    if continues_usage(request.text, history):
        attach_cards = False
        targets = resolve_usage_targets(request.text, history, rows)
        reply = (
            usage_text(targets)
            if targets
            else "Bạn gửi giúp shop mã hoặc tên sản phẩm vừa xem, shop đọc hướng dẫn dùng cho đúng sản phẩm đó."
        )
        grounding: list[str] = []
    elif asks_for_image(request.text):
        reply = image_reply(request.text, history, rows, request.channel, request.channel_owned)
        grounding = []
    elif choose_tool(request.text) == "get_price_and_stock":
        reply = answer_price_and_stock(
            request.text, rows, channel=request.channel, owned=request.channel_owned
        )
        grounding = []
    else:
        constraints = parse_constraints(request.text)
        ranked_at = time.perf_counter()
        hits = (
            search_product(request.text, rows, channel=request.channel, owned=request.channel_owned)
            if constraints.active
            else []
        )
        if constraints.active and loaded is not None and not hits:
            rank_ms = _ms_since(ranked_at)
            reply = "Shop chưa có sản phẩm đang bán khớp yêu cầu này."
            grounding = []
        else:
            shown = hits or search_product(
                request.text, rows, channel=request.channel, owned=request.channel_owned, limit=PROMPT_PRODUCTS
            )
            rank_ms = _ms_since(ranked_at)
            catalog = catalog_snapshot(shown, request.channel, request.channel_owned)
            asked_at = time.perf_counter()
            reply = provider.generate(request, system_prompt_for(request, list(catalog.lines)))
            llm_ms = _ms_since(asked_at)
            # Giá chỉ được giữ khi khớp đúng các dòng vừa đưa cho LLM. Tồn kho và khuyến mãi vẫn bị xóa (T-06.6).
            reply, grounding = strip_unverified_numbers(reply, set(catalog.prices))
            if hits:
                lines = suggestion_lines(hits, request.channel, request.channel_owned)
                reply = "Gợi ý trong shop:\n" + "\n".join(lines) + "\n" + reply
    if attach_cards:
        reply = ensure_product_cards(reply, rows, request.channel, request.channel_owned)
    if warnings:
        reply = "Lưu ý: " + " ".join(w.message for w in warnings) + "\n" + reply

    # Để T-43.2 tách phần đọc DB / xếp hạng khỏi thời gian LLM. Không log câu hỏi của khách.
    log.info(
        "chat %s db_ms=%.0f rank_ms=%.0f llm_ms=%.0f catalog_rows=%d",
        request.message_id,
        db_ms,
        rank_ms,
        llm_ms,
        len(rows),
    )
    return ChatResponse(reply=reply, provider=provider.name, warnings=[w.code for w in warnings] + grounding)
