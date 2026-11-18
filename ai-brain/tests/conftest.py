"""Cấu hình chung cho pytest: app khởi động được mà không cần key thật, và không test nào gọi mạng.

App chỉ có provider thật (decisions #25), nên ở đây đặt biến môi trường giả TRƯỚC khi test import app.main,
rồi thay provider bằng bản trong tests/stubs.py cho từng test. Catalog: test nào cần thì tự thay
app.agent.graph.load_search_rows; không thay thì brain thử CHATBOT_DB_URL giả (cổng 1), lỗi, chạy như mất database.
"""

import os

import pytest

os.environ["LLM_PROVIDER"] = "gemini"
os.environ["LLM_MODEL"] = ""
os.environ["LLM_API_KEY"] = "test-key"
os.environ["CHATBOT_DB_URL"] = "postgresql://chatbot_reader:test@localhost:1/none"

from tests.stubs import StubProvider  # noqa: E402


@pytest.fixture(autouse=True)
def offline(monkeypatch):
    import app.catalog_access
    import app.main
    import app.rag.search

    monkeypatch.setattr(app.main, "provider", StubProvider())
    # Cache catalog và thời gian ngừng thử kết nối là trạng thái của module: mỗi test bắt đầu sạch.
    app.rag.search.clear_cache()
    app.catalog_access.reset()
    yield
    app.rag.search.clear_cache()
    app.catalog_access.reset()
