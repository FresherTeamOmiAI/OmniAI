"""10 ca mẫu: agent chọn đúng tool. US-15. Không gọi mạng."""

from app.agent.loop import choose_tool

CASES = [
    ("CERA-CLE-01 giá bao nhiêu", "get_price_and_stock"),
    ("CERA-CLE-01 còn hàng không", "get_price_and_stock"),
    ("tồn kho của CERA-CLE-01 là bao nhiêu", "get_price_and_stock"),
    ("Nivea Derma Skin Clear UV Fluid giá bao nhiêu", "get_price_and_stock"),
    ("sữa rửa mặt này còn không shop", "get_price_and_stock"),
    ("kem chống nắng cho da dầu dưới 300k", "search_product"),
    ("gợi ý serum vitamin C cho da dầu", "search_product"),
    ("toner cho da dầu", "search_product"),
    ("chào shop", "none"),
    ("shop có giảm giá không", "none"),
]


def test_ten_samples_pick_the_right_tool():
    assert len(CASES) == 10
    for text, expected in CASES:
        assert choose_tool(text) == expected, text
