import pytest

from app.rules.grounding import NOTE, strip_unverified_numbers

FAKE_PRICE = (
    "Dạ sản phẩm này đang có giá 450.000đ, còn 15 sản phẩm và đang giảm 10% ạ. "
    "Bạn nên thoa sau bước dưỡng ẩm nhé."
)


def test_removes_invented_price_stock_and_promo():
    reply, codes = strip_unverified_numbers(FAKE_PRICE)

    assert reply == "Bạn nên thoa sau bước dưỡng ẩm nhé.\n" + NOTE
    assert codes == ["grounding:price", "grounding:stock", "grounding:promo"]


@pytest.mark.parametrize(
    ("text", "code"),
    [
        ("Giá chỉ 289k thôi ạ.", "grounding:price"),
        ("Sản phẩm giá 1.200 VNĐ.", "grounding:price"),
        ("Giá 289,000 đ nhé.", "grounding:price"),
        ("Giá khoảng 450 nghìn ạ.", "grounding:price"),
        ("Combo này 1,2 triệu.", "grounding:price"),
        ("Tồn kho: 120.", "grounding:stock"),
        ("Shop còn lại 3 chai thôi.", "grounding:stock"),
        ("Đang sale 50% đến hết tuần.", "grounding:promo"),
        ("Hôm nay giảm giá 20% bạn nhé.", "grounding:promo"),
    ],
)
def test_detects_number_formats(text, code):
    reply, codes = strip_unverified_numbers(text)

    assert reply == NOTE
    assert code in codes


@pytest.mark.parametrize(
    "text",
    [
        "Serum Retinol 0.3% của Sen Việt không nên dùng khi mang thai.",
        "Dung dịch BHA 2% của Lumi Lab giúp làm sạch lỗ chân lông.",
        "Kem chống nắng SPF50+ PA++++ dung tích 50 ml.",
        "Bạn dùng 2-3 lần/tuần, từ 3 đến 5 đêm rồi tăng dần.",
        "Niacinamide giúp giảm thâm sau 4 tuần.",
        "Bạn còn thắc mắc gì không ạ?",
    ],
)
def test_keeps_text_without_prices(text):
    assert strip_unverified_numbers(text) == (text, [])


def test_keeps_other_lines_and_drops_empty_list_item():
    text = (
        "Gợi ý cho da dầu:\n"
        "1. **Sữa rửa mặt** dịu nhẹ.\n"
        "2. **Kem chống nắng**: giá 289.000đ.\n"
        "\n"
        "Bạn dùng buổi sáng nhé."
    )

    reply, codes = strip_unverified_numbers(text)

    assert reply == "Gợi ý cho da dầu:\n1. **Sữa rửa mặt** dịu nhẹ.\n\nBạn dùng buổi sáng nhé.\n" + NOTE
    assert codes == ["grounding:price"]
