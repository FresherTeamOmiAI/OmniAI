from bench.policy_check import load_policies
from bench.run import grade, ws_url

POLICIES = load_policies()


def make_case(channel="WEB", **expect):
    return {"id": "T-1", "category": "test", "channel": channel, "text": "...", "expect": expect}


def make_reply(final_text, blocked=False, raw_violations=()):
    return {"finalText": final_text, "blocked": blocked, "violations": [{"ruleId": r} for r in raw_violations]}


def test_ws_url_is_derived_from_base_url():
    assert ws_url("http://localhost:8080") == "ws://localhost:8080/ws/chat"
    assert ws_url("http://localhost:8080/") == "ws://localhost:8080/ws/chat"
    assert ws_url("https://omni.example") == "wss://omni.example/ws/chat"


def test_clean_answer_passes():
    result = grade(POLICIES, make_case(must_contain_any=["chống nắng"]), make_reply("Kem chống nắng SPF50+ ạ"), 120)

    assert result["passed"]
    assert result["failures"] == []
    assert result["transport"] == "simulator"


def test_violation_left_in_final_text_fails():
    result = grade(POLICIES, make_case("SHOPEE"), make_reply("Xem tại https://omni-demo.local"), 120)

    assert not result["passed"]
    assert result["final_violations"] == ["no-external-link"]


def test_wrong_blocked_flag_fails():
    assert not grade(POLICIES, make_case("SHOPEE", blocked=True), make_reply("Dạ shop tư vấn ạ"), 120)["passed"]


def test_missing_expected_text_fails():
    assert not grade(POLICIES, make_case(must_contain_any=["bác sĩ"]), make_reply("Dạ shop tư vấn ạ"), 120)["passed"]


def test_forbidden_pattern_fails():
    case = make_case(must_not_match=r"\d[\d.,]*\s*(đ|k)(?!\w)")

    assert grade(POLICIES, case, make_reply("Giá shop sẽ kiểm tra rồi báo bạn ạ"), 120)["passed"]
    result = grade(POLICIES, case, make_reply("Giá chỉ 450.000đ ạ"), 120)
    assert not result["passed"]
    assert result["failures"] == ["có đoạn cấm «450.000đ»"]


def test_slow_answer_fails():
    assert grade(POLICIES, make_case(), make_reply("Dạ"), 5000)["passed"]
    assert not grade(POLICIES, make_case(), make_reply("Dạ"), 5001)["passed"]
    assert not grade(POLICIES, make_case(max_latency_ms=1000), make_reply("Dạ"), 1001)["passed"]


def test_raw_violations_come_from_bot_reply():
    reply = make_reply("Dạ, shop xin phép tư vấn trong phạm vi Shopee ạ.", blocked=True, raw_violations=["no-other-channel"])

    result = grade(POLICIES, make_case("SHOPEE", blocked=True), reply, 50)

    assert result["passed"]
    assert result["raw_violations"] == ["no-other-channel"]


# ---------- transport tiktok (simulator TikTok Shop) ----------

import json  # noqa: E402

import pytest  # noqa: E402

from bench import run  # noqa: E402
from bench.policy_check import load_fallbacks  # noqa: E402

FALLBACKS = load_fallbacks()


class FakeResponse:
    def __init__(self, body):
        self._body = body

    def raise_for_status(self):
        pass

    def json(self):
        return self._body


def sim_reply(text, latency=1200):
    return {"conversation_id": "c1", "message_id": "m1", "reply": {
        "content": json.dumps({"content": text}, ensure_ascii=False), "sender": {"role": "ROBOT"}},
        "reply_latency_ms": latency}


def tiktok_case():
    return {"id": "TTK-T", "channel": "TIKTOK", "transport": "tiktok", "text": "hi"}


def test_fallbacks_are_loaded_per_channel():
    assert FALLBACKS["TIKTOK"].startswith("Dạ, shop xin phép tư vấn trong phạm vi TikTok Shop")
    assert "*" not in FALLBACKS


def test_tiktok_reply_becomes_final_text(monkeypatch):
    sent = {}

    def fake_post(url, json, timeout):
        sent.update(url=url, body=json)
        return FakeResponse(sim_reply("Dạ kem chống nắng kiềm dầu ạ"))

    monkeypatch.setattr(run.httpx, "post", fake_post)
    reply, latency = run.send_tiktok("http://sim:8090/", tiktok_case(), "bench-1", FALLBACKS)

    assert sent["url"] == "http://sim:8090/sim/buyer/messages"
    assert sent["body"]["buyer_user_id"] == "bench-1" and sent["body"]["wait_ms"] > 0
    assert reply == {"finalText": "Dạ kem chống nắng kiềm dầu ạ", "blocked": False, "violations": []}
    assert latency == 1200


def test_tiktok_fallback_means_blocked(monkeypatch):
    monkeypatch.setattr(run.httpx, "post", lambda url, json, timeout: FakeResponse(sim_reply(FALLBACKS["TIKTOK"])))
    reply, _ = run.send_tiktok("http://sim", tiktok_case(), "bench-2", FALLBACKS)
    assert reply["blocked"] is True


def test_tiktok_without_reply_is_an_error(monkeypatch):
    monkeypatch.setattr(run.httpx, "post", lambda url, json, timeout: FakeResponse({"conversation_id": "c1", "reply": None}))
    with pytest.raises(RuntimeError, match="không trả lời"):
        run.send_tiktok("http://sim", tiktok_case(), "bench-3", FALLBACKS)


def test_tiktok_transport_only_for_tiktok_channel():
    with pytest.raises(ValueError, match="chỉ dùng cho kênh TIKTOK"):
        run.run_case(None, POLICIES, {**tiktok_case(), "channel": "SHOPEE"})


def test_tiktok_dataset_has_five_cases():
    lines = (run.ROOT / "datasets" / "tiktok-flow.jsonl").read_text(encoding="utf-8").splitlines()
    cases = [json.loads(line) for line in lines if line.strip()]
    assert len(cases) == 5
    assert {c["channel"] for c in cases} == {"TIKTOK"} and {c["transport"] for c in cases} == {"tiktok"}
