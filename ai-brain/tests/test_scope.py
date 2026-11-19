from app.rules.scope import scope_block
from tests.test_api import chat

FALLBACK = "mình chỉ tư vấn mỹ phẩm trong shop"


def test_math_expression_is_refused_without_calling_the_model():
    body = chat("1 + 1 bằng mấy")
    assert FALLBACK in body["reply"]
    assert body["provider"] == "policy"
    assert body["warnings"] == ["scope:math"]
    assert body["handoff"] is False


def test_math_words_and_other_subjects_are_refused():
    assert scope_block("giải phương trình x cộng 1").rule_id == "math"
    assert scope_block("2 nhân 8").rule_id == "math"
    assert scope_block("viết hàm python tính tổng").rule_id == "programming"
    assert scope_block("Java là gì").rule_id == "programming"
    assert scope_block("HTML viết thế nào").rule_id == "programming"
    assert scope_block("phân tích Truyện Kiều").rule_id == "literature"
    assert scope_block("giá trị của triết học là gì").rule_id == "philosophy"
    assert scope_block("nhà Trần trong lịch sử đánh quân Nguyên năm nào").rule_id == "history"
    assert scope_block("sông nào dài nhất Việt Nam").rule_id == "geography"
    assert scope_block("địa lý châu Á có những nước nào").rule_id == "geography"
    assert scope_block("thủ đô của Pháp là gì").rule_id == "geography"
    assert scope_block("hôm nay thời tiết thế nào").rule_id == "other-subject"
    assert scope_block("kể một chuyện cười") is None


def test_shop_questions_pass_the_scope_policy():
    allowed = [
        "da dầu thì dùng sản phẩm nào",
        "Kem chống nắng cho da dầu",
        "ANUA-MOI-01 giá bao nhiêu",
        "SPF50+ dùng được không",
        "dùng 1-2 lần mỗi ngày được không",
        "serum 10% BHA có hợp da dầu không",
        "dưới 300k có kem chống nắng không",
        "Mình đang mang thai, dùng retinol được không?",
        "xin chào",
        "cho mình xin tư vấn",
        "shop ơi tư vấn giúp mình",
        "có gì phù hợp không",
        "nên chọn gì",
    ]
    for text in allowed:
        assert scope_block(text) is None, text
