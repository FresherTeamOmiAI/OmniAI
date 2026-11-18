"""Catalog nhớ trong bộ nhớ, đủ SKU, prompt gọn, giá NULL, ngừng thử khi mở kết nối lỗi (US-43, T-43.4). Owner: LucVT."""

import logging
from types import SimpleNamespace

import psycopg
import pytest

import app.catalog_access as catalog_access
import app.main
import app.rag.search as search
from app.agent.graph import PROMPT_PRODUCTS
from app.agent.product_card import NOT_ON_CHANNEL, image_reply
from app.rag.search import ProductRow, catalog_snapshot, load_search_rows, search_product
from tests.test_api import chat


def _record(sku, brand, name, category="toner", skin="dry", web=500_000, shopee=None, tiktok=None):
    shopee = web if shopee is None else shopee
    tiktok = web if tiktok is None else tiktok
    return (sku, brand, name, category, skin, "", "am", web, shopee, tiktok, "", None)


NIVEA = _record("NIVE-SUN-02", "Nivea", "Nivea Derma Skin Clear UV Fluid SPF50", "sunscreen", "oily", 201_000, 210_000, 199_000)
# 100 toner chiếm trang đầu (thứ tự sku), sản phẩm đúng nằm ở trang 2 như catalog thật (364 SKU).
FIRST_PAGE = [_record(f"AAA-TON-{i:03d}", "Khác", f"Toner hoa hồng số {i}", web=400_000 + i * 1_000) for i in range(100)]


class CountingPages:
    """Thay run_select: trả trang theo OFFSET và đếm số câu SELECT tới database."""

    def __init__(self, pages):
        self.pages = pages
        self.calls = 0

    def __call__(self, sql: str):
        self.calls += 1
        offset = int(sql.rsplit("OFFSET", 1)[1]) if "OFFSET" in sql else 0
        return self.pages[offset // 100] if offset // 100 < len(self.pages) else []


class RecordingProvider:
    name = "stub"

    def __init__(self, reply="Dạ shop gợi ý bạn vài sản phẩm ạ."):
        self.reply = reply
        self.prompts = []

    def generate(self, request, system_prompt):
        self.prompts.append(system_prompt)
        return self.reply


@pytest.fixture
def pages(monkeypatch):
    counting = CountingPages([FIRST_PAGE, [NIVEA]])
    monkeypatch.setattr(search, "run_select", counting)
    return counting


@pytest.fixture
def clock(monkeypatch):
    now = {"t": 1_000.0}
    monkeypatch.setattr(search, "time", SimpleNamespace(monotonic=lambda: now["t"]))
    return now


def test_ten_chats_within_ttl_read_the_database_once(pages, clock):
    for _ in range(10):
        chat("kem chống nắng cho da dầu dưới 300k")
    assert pages.calls == 2  # 1 lần nạp = 2 trang (100 + 1 SKU), không phải 2 câu mỗi lượt chat


def test_cache_expires_after_ttl(pages, clock, monkeypatch):
    monkeypatch.setenv("CATALOG_CACHE_TTL_SECONDS", "60")
    assert len(load_search_rows()) == 101
    clock["t"] += 59
    load_search_rows()
    assert pages.calls == 2
    clock["t"] += 2
    load_search_rows()
    assert pages.calls == 4


def test_database_failure_is_not_cached(monkeypatch, clock):
    state = {"down": True}

    def flaky(sql):
        if state["down"]:
            raise psycopg.OperationalError("down")
        return [NIVEA]

    monkeypatch.setattr(search, "run_select", flaky)
    assert load_search_rows() is None
    state["down"] = False
    assert [row.sku for row in load_search_rows()] == ["NIVE-SUN-02"]


def test_vectors_are_computed_once_at_load(pages, clock):
    rows = load_search_rows()
    assert all(row.embedding is not None and len(row.embedding) == 768 for row in rows)


def test_sku_after_the_first_hundred_keeps_its_price(pages, clock, monkeypatch):
    provider = RecordingProvider("Dạ Nivea Derma Skin Clear UV Fluid SPF50 giá 201.000đ, hợp da dầu ạ.")
    monkeypatch.setattr(app.main, "provider", provider)
    body = chat("kem chống nắng cho da dầu dưới 300k")
    assert "giá 201.000đ, hợp da dầu" in body["reply"]
    assert "grounding:price" not in body["warnings"]


def test_prompt_gets_nearest_products_not_the_whole_catalog(pages, clock, monkeypatch):
    provider = RecordingProvider()
    monkeypatch.setattr(app.main, "provider", provider)
    chat("nước hoa hồng nào dịu nhẹ?")
    prompt = provider.prompts[-1]
    assert prompt.count("Toner hoa hồng số") == PROMPT_PRODUCTS
    assert "Nivea" not in prompt


def test_price_outside_the_prompt_lines_is_stripped(pages, clock, monkeypatch):
    """Whitelist giá = giá của đúng các dòng đưa cho LLM, không phải cả catalog (T-06.6)."""
    provider = RecordingProvider("Dạ toner hoa hồng rất dịu. Bạn cũng có thể thử Nivea giá 201.000đ ạ.")
    monkeypatch.setattr(app.main, "provider", provider)
    body = chat("nước hoa hồng nào dịu nhẹ?")
    assert "201.000đ" not in body["reply"]
    assert "grounding:price" in body["warnings"]


def test_null_price_on_a_channel_does_not_break_chat(monkeypatch, clock):
    paused = _record("LUMI-SUN-01", "Lumi Lab", "Kem chống nắng kiềm dầu SPF50+ PA++++", "sunscreen", "oily", 289_000, 299_000)
    paused = paused[:9] + (None,) + paused[10:]  # chưa có giá TikTok
    monkeypatch.setattr(search, "run_select", CountingPages([[paused, NIVEA]]))

    tiktok = chat("kem chống nắng cho da dầu dưới 300k", channel="TIKTOK", owned=False)
    assert "Lumi Lab" not in tiktok["reply"]
    assert "Nivea" in tiktok["reply"]
    web = chat("kem chống nắng cho da dầu dưới 300k")
    assert "Lumi Lab — Kem chống nắng kiềm dầu SPF50+ PA++++ — 289.000đ" in web["reply"]


def test_snapshot_lists_only_prices_the_channel_may_see():
    row = ProductRow("A-B-01", "Lumi", "Kem A", "sunscreen", "oily", "", "am", 289_000, 299_000, None)
    owned = catalog_snapshot([row], "WEB", True)
    assert owned.lines == ("Lumi — Kem A (sunscreen, oily, am) — giá web 289.000đ, Shopee 299.000đ",)
    assert owned.prices == {289_000, 299_000}
    shopee = catalog_snapshot([row], "SHOPEE", False)
    assert shopee.lines == ("Lumi — Kem A (sunscreen, oily, am) — giá 299.000đ",) and shopee.prices == {299_000}
    assert catalog_snapshot([row], "TIKTOK", False).lines == ()


def test_image_of_a_product_without_price_on_the_channel():
    row = ProductRow("A-B-01", "Lumi", "Kem chống nắng A dài tên", "sunscreen", "oily", "", "am", 289_000, 299_000, None)
    assert image_reply("ảnh Kem chống nắng A dài tên", [], [row], "TIKTOK", False) == NOT_ON_CHANNEL


def test_cheapest_and_priciest_sort_by_channel_price():
    rows = [ProductRow(f"X-Y-{i:02d}", "H", f"Toner {i}", "toner", "dry", "", "", web, web, web) for i, web in enumerate((300_000, 100_000, 900_000))]
    assert search_product("toner rẻ nhất", rows)[0].price_web == 100_000
    assert search_product("sản phẩm đắt nhất", rows, limit=1)[0].price_web == 900_000


def test_chat_logs_stage_timings(pages, clock, caplog):
    with caplog.at_level(logging.INFO, logger="app.agent.graph"):
        chat("kem chống nắng cho da dầu dưới 300k")
    line = next(record.getMessage() for record in caplog.records if "db_ms=" in record.getMessage())
    assert "rank_ms=" in line and "llm_ms=" in line and "catalog_rows=101" in line
    assert "kem chống nắng" not in line  # không log câu hỏi của khách


class FailingConnect:
    def __init__(self, message):
        self.message = message
        self.calls = 0

    def __call__(self, url, **kwargs):
        self.calls += 1
        raise psycopg.OperationalError(self.message)


def test_failed_login_stops_retrying_for_a_while(monkeypatch, caplog):
    now = {"t": 5_000.0}
    monkeypatch.setattr(catalog_access, "time", SimpleNamespace(monotonic=lambda: now["t"]))
    connect = FailingConnect('connection to server at "db.example" failed: FATAL: password authentication failed for user "chatbot_reader"')
    monkeypatch.setattr(catalog_access.psycopg, "connect", connect)

    with caplog.at_level(logging.WARNING, logger="app.catalog_access"):
        with pytest.raises(psycopg.OperationalError):
            catalog_access.run_select(search.SEARCH_SQL)
        for _ in range(5):  # 5 câu chat tiếp theo không thử đăng nhập lại
            with pytest.raises(catalog_access.CatalogUnavailable):
                catalog_access.run_select(search.SEARCH_SQL)
    assert connect.calls == 1
    assert "sai user hoặc mật khẩu" in caplog.text
    assert "db.example" not in caplog.text and "localhost:1" not in caplog.text and "test@" not in caplog.text

    now["t"] += catalog_access.CONNECT_BACKOFF_S + 1
    with pytest.raises(psycopg.OperationalError):
        catalog_access.run_select(search.SEARCH_SQL)
    assert connect.calls == 2


def test_circuit_breaker_message_is_named(monkeypatch, caplog):
    monkeypatch.setattr(catalog_access.psycopg, "connect", FailingConnect("FATAL: (ECIRCUITBREAKER) too many authentication failures"))
    with caplog.at_level(logging.WARNING, logger="app.catalog_access"):
        with pytest.raises(psycopg.OperationalError):
            catalog_access.run_select(search.SEARCH_SQL)
    assert "ECIRCUITBREAKER" in caplog.text


def test_chat_keeps_working_while_database_backs_off(monkeypatch):
    connect = FailingConnect("password authentication failed")
    monkeypatch.setattr(catalog_access.psycopg, "connect", connect)
    for _ in range(3):
        assert chat("kem chống nắng cho da dầu dưới 300k")["reply"]
    assert connect.calls == 1


class FakeCursor:
    def __init__(self, sent):
        self.sent = sent
        self.results = [None, None, [("row",)], None]  # BEGIN, SET LOCAL, SELECT, COMMIT
        self.description = None

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql):
        self.sent.append(sql)
        self.description = self.results[0] and ("col",)

    def fetchall(self):
        return self.results[0]

    def nextset(self):
        self.results.pop(0)
        if not self.results:
            return None
        self.description = self.results[0] and ("col",)
        return True


class FakeConnection:
    closed = False

    def __init__(self):
        self.sent = []

    def cursor(self):
        return FakeCursor(self.sent)

    def close(self):
        self.closed = True


def test_select_runs_read_only_with_timeout_in_one_round_trip(monkeypatch):
    connection = FakeConnection()
    monkeypatch.setattr(catalog_access.psycopg, "connect", lambda url, **kwargs: connection)
    assert catalog_access.run_select(search.SEARCH_SQL) == [("row",)]
    assert len(connection.sent) == 1
    sent = connection.sent[0]
    assert sent.startswith("BEGIN READ ONLY; SET LOCAL statement_timeout = '2s'; SELECT sku")
    assert sent.endswith("; COMMIT")
