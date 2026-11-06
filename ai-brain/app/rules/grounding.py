"""Bỏ con số giá, tồn kho, khuyến mãi mà LLM tự nêu (ràng buộc C-07, T-06.6).

AI Brain chưa có tool nào (agent loop có `get_price_and_stock` ở S3, US-15), nên mọi con số loại này
trong câu của LLM đều chưa được kiểm chứng. Câu nào chứa số đó thì bỏ cả câu, rồi thêm một câu hẹn
kiểm tra. Đây là code tất định có pytest, giống `ingredients.py`. Khi có tool, US-15 sẽ chỉ cho qua
những số khớp kết quả tool trong lượt đó.
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


def _found(text: str) -> list[str]:
    return [code for code, pattern in CHECKS if pattern.search(text)]


def strip_unverified_numbers(reply: str) -> tuple[str, list[str]]:
    """Bỏ các câu có giá, tồn kho, khuyến mãi do LLM tự nêu. Trả về (câu trả lời mới, mã cảnh báo).

    Dòng không chứa số loại đó được giữ nguyên định dạng. Nếu có bỏ câu nào thì thêm NOTE ở cuối.
    """
    codes: list[str] = []
    lines: list[str] = []
    for line in reply.split("\n"):
        if not _found(line):
            lines.append(line)
            continue
        kept = []
        for sentence in _SENTENCE_END.split(line):
            found = _found(sentence)
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
