"""Bản thay thế chỉ dùng trong pytest: không gọi LLM, không nối database.

Không nằm trong app/ và không chọn được qua LLM_PROVIDER (decisions #25). StubProvider cố tình bịa giá,
tồn kho, khuyến mãi khi được hỏi giá, để test thấy grounding.py bỏ các số đó.
"""

from app.catalog_access import CatalogSnapshot
from app.schemas import ChatRequest

# Giá của đúng 1 sản phẩm, giống dữ liệu migration V5. Dùng thay cho view chatbot_catalog.
CATALOG = CatalogSnapshot(
    lines=("Lumi Lab — Kem chống nắng kiềm dầu SPF50+ (sunscreen, oily, am) — giá web 289.000đ, Shopee 299.000đ, TikTok 279.000đ",),
    prices=frozenset({289_000, 299_000, 279_000}),
)

INVENTED_PRICE = (
    "Dạ sản phẩm này đang có giá 450.000đ, còn 15 sản phẩm và đang giảm 10% ạ. "
    "Bạn nên thoa sau bước dưỡng ẩm nhé."
)
CATALOG_PRICE = "Dạ Kem chống nắng kiềm dầu SPF50+ của Lumi Lab giá 289.000đ ạ."


class StubProvider:
    name = "stub"

    def generate(self, request: ChatRequest, system_prompt: str) -> str:
        text = request.text.lower()
        if "kiềm dầu" in text and "giá" in text:
            return CATALOG_PRICE
        if "giá" in text or "bao nhiêu" in text:
            return INVENTED_PRICE
        return f"Dạ shop đã nhận câu hỏi: «{request.text}». Bạn cho shop biết thêm loại da của bạn nhé!"
