"""Giá và tồn lấy từ API, hết hàng thì nói hết hàng. US-15."""

from app.rag.search import ProductRow
from app.tools.price_stock import PriceStock, answer_price_and_stock, format_price_stock


def _item(stock: int) -> PriceStock:
    return PriceStock("CERA-CLE-01", "CeraVe", "Sữa rửa mặt", "cleanser", {"WEB": 186000, "SHOPEE": 190000}, stock)


def test_in_stock_uses_the_channel_price():
    text = format_price_stock(_item(8), "SHOPEE", False)
    assert "190.000đ" in text
    assert "còn 8" in text
    assert "186.000" not in text


def test_out_of_stock_says_so_and_offers_another_item():
    other = PriceStock("NEUT-CLE-06", "Neutrogena", "Oil-Free Acne Wash", "cleanser", {"WEB": 186000}, 4)
    text = format_price_stock(_item(0), "WEB", True, other)
    assert "hết hàng" in text
    assert "Oil-Free Acne Wash" in text
    assert "còn 4" in text


def test_answer_reads_the_backend_number(monkeypatch):
    monkeypatch.setattr("app.tools.price_stock.fetch_price_stock", lambda sku, client=None: _item(3))
    text = answer_price_and_stock("CERA-CLE-01 còn hàng không", [], channel="WEB", owned=True)
    assert "186.000đ" in text
    assert "còn 3" in text


def test_unknown_product_does_not_invent_a_price():
    rows = [ProductRow("X-SUN-01", "A", "Kem khác", "sunscreen", "oily", "", "am", 1, 1, 1)]
    text = answer_price_and_stock("giá bao nhiêu", rows, channel="WEB", owned=True)
    assert "mã hoặc tên" in text
    assert "000" not in text
