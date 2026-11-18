"""Bản thay thế chỉ dùng trong pytest: không gọi LLM, không nối database.

Không nằm trong app/ và không chọn được qua LLM_PROVIDER (decisions #25). StubProvider cố tình bịa giá,
tồn kho, khuyến mãi khi được hỏi giá, để test thấy grounding.py bỏ các số đó.
"""

from app.schemas import ChatRequest

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
