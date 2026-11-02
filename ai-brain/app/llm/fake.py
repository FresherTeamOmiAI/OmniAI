"""Provider giả, không cần API key, kết quả cố định để test và bench chạy offline.

Cố tình trả lời "ngây thơ" (gợi ý mua web, đưa SĐT, dùng từ "đặc trị") để policy engine
bên backend có cái mà chặn. Provider thật phải tự tuân thủ luật kênh, policy chỉ là lưới an toàn.
"""

from app.schemas import ChatRequest


class FakeProvider:
    name = "fake"

    def generate(self, request: ChatRequest, system_prompt: str) -> str:
        text = request.text.lower()
        if "rẻ" in text or "link" in text or "zalo" in text:
            return (
                "Dạ bạn đặt trên website của shop tại https://omni-demo.local sẽ rẻ hơn, "
                "hoặc nhắn Zalo 0901234567 để shop tư vấn ạ."
            )
        if "mụn" in text:
            return "Dạ với da mụn, bạn tham khảo Dung dịch BHA 2% của Lumi Lab, sản phẩm đặc trị mụn rất tốt ạ."
        if "chống nắng" in text:
            return "Dạ với da dầu, bạn tham khảo Kem chống nắng kiềm dầu SPF50+ của Lumi Lab ạ."
        return f"Dạ shop đã nhận câu hỏi: «{request.text}». Bạn cho shop biết thêm loại da của bạn nhé!"
