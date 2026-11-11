"""Cấu hình chung cho pytest: app khởi động được mà không cần key thật, và không test nào gọi mạng.

App chỉ có provider thật (decisions #25), nên ở đây đặt biến môi trường giả TRƯỚC khi test import app.main,
rồi thay provider và hàm đọc catalog bằng bản trong tests/stubs.py cho từng test.
"""

import os

import pytest

os.environ["LLM_PROVIDER"] = "gemini"
os.environ["LLM_MODEL"] = ""
os.environ["LLM_API_KEY"] = "test-key"
os.environ["CHATBOT_DB_URL"] = "postgresql://chatbot_reader:test@localhost:1/none"

from tests.stubs import CATALOG, StubProvider  # noqa: E402


@pytest.fixture(autouse=True)
def offline(monkeypatch):
    import app.agent.graph
    import app.main

    monkeypatch.setattr(app.main, "provider", StubProvider())
    monkeypatch.setattr(app.agent.graph, "load_public_catalog", lambda channel="WEB", channel_owned=True: CATALOG)
