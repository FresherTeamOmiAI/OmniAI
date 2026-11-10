"""Bỏ con số giá, tồn kho, khuyến mãi mà LLM tự nêu (ràng buộc C-07, T-06.6).

Giá được giữ khi từng số tiền trong câu khớp một giá đã đọc từ view chatbot_catalog ở lượt đó.
Tồn kho và khuyến mãi vẫn bỏ, vì catalog chưa đưa hai dữ liệu này. Câu bị bỏ thì thêm một câu hẹn
kiểm tra.
"""

import re

# Số tiền: 450.000đ, 289k, 1.200 VNĐ, 450 nghìn, 1,2 triệu. (?!\w) để "3 đến 5", "5 đêm" không khớp.
PRICE = re.compile(r"\d[\d.,]*\s*(?:đồng|đ|₫|vnđ|vnd|k|nghìn|ngàn|triệu)(?!\w)", re.IGNORECASE)
# Tồn kho: "tồn 15", "tồn kho: 120", "còn 15 sản phẩm", "còn lại 3 chai".
STOCK = re.compile(
    r"tồn(?:\s*kho)?\s*:?\s*\d+"
    r"|còn\s*(?:lại\s*)?\d+\s*(?:sản phẩm|sp|hộp|chai|tuýp|lọ|cái|hũ|gói|món|suất|chiếc|bộ)(?!\w)",
    re.IGNORECASE,
)
# Khuyến mãi: chỉ bắt % đi sau từ khóa giảm giá, để nồng độ như "Retinol 0.3%", "BHA 2%" không bị bắt.
PROMO = re.compile(
    r"(?:giảm|sale|khuyến mãi|ưu đãi|chiết khấu)\s*(?:giá\s*)?(?:đến\s*|tới\s*|còn\s*)?\d+(?:[.,]\d+)?\s*%",
    re.IGNORECASE,
)
CHECKS = (("grounding:price", PRICE), ("grounding:stock", STOCK), ("grounding:promo", PROMO))

NOTE = "Giá, tồn kho và khuyến mãi chính xác shop sẽ kiểm tra rồi báo bạn ngay ạ."

_SENTENCE_END = re.compile(r"(?<=[.!?…])\s+")
_ONLY_LIST_MARKER = re.compile(r"^\s*(?:\d+[.)]|[-*•])?\s*$")


def _amount(text: str) -> int | None:
    match = PRICE.search(text)
    if match is None:
        return None
    number, unit = match.group(0), ""
    unit_match = re.search(r"(đồng|đ|₫|vnđ|vnd|k|nghìn|ngàn|triệu)", match.group(0), re.IGNORECASE)
    if unit_match:
        unit = unit_match.group(1).lower()
        number = match.group(0)[: unit_match.start()]
    digits = number.strip().replace(" ", "")
    try:
        if unit == "k":
            return int(float(digits.replace(".", "").replace(",", ".")) * 1_000)
        if unit in {"nghìn", "ngàn"}:
            return int(float(digits.replace(".", "").replace(",", ".")) * 1_000)
        if unit == "triệu":
            return int(float(digits.replace(".", "").replace(",", ".")) * 1_000_000)
        return int(digits.replace(".", "").replace(",", ""))
    except ValueError:
        return None


def _prices_are_allowed(text: str, allowed_prices: set[int]) -> bool:
    if not allowed_prices:
        return False
    amounts = [_amount(match.group(0)) for match in PRICE.finditer(text)]
    return bool(amounts) and all(amount in allowed_prices for amount in amounts)


def _found(text: str, allowed_prices: set[int]) -> list[str]:
    codes = []
    for code, pattern in CHECKS:
        if not pattern.search(text):
            continue
        if code == "grounding:price" and _prices_are_allowed(text, allowed_prices):
            continue
        codes.append(code)
    return codes


def strip_unverified_numbers(reply: str, allowed_prices: set[int] | None = None) -> tuple[str, list[str]]:
    """Bỏ các câu có giá, tồn kho, khuyến mãi do LLM tự nêu. Trả về (câu trả lời mới, mã cảnh báo).

    Giá nằm trong allowed_prices được giữ. Dòng không chứa số loại đó được giữ nguyên định dạng.
    Nếu có bỏ câu nào thì thêm NOTE ở cuối.
    """
    allowed = allowed_prices or set()
    codes: list[str] = []
    lines: list[str] = []
    for line in reply.split("\n"):
        if not _found(line, allowed):
            lines.append(line)
            continue
        kept = []
        for sentence in _SENTENCE_END.split(line):
            found = _found(sentence, allowed)
            codes += [code for code in found if code not in codes]
            if not found:
                kept.append(sentence)
        text = " ".join(kept)
        if not _ONLY_LIST_MARKER.match(text):
            lines.append(text)

    if not codes:
        return reply, []
    cleaned = "\n".join(lines).strip()
    return (f"{cleaned}\n{NOTE}" if cleaned else NOTE), codes
