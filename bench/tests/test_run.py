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


def test_slow_answer_fails():
    assert grade(POLICIES, make_case(), make_reply("Dạ"), 5000)["passed"]
    assert not grade(POLICIES, make_case(), make_reply("Dạ"), 5001)["passed"]
    assert not grade(POLICIES, make_case(max_latency_ms=1000), make_reply("Dạ"), 1001)["passed"]


def test_raw_violations_come_from_bot_reply():
    reply = make_reply("Dạ, shop xin phép tư vấn trong phạm vi Shopee ạ.", blocked=True, raw_violations=["no-other-channel"])

    result = grade(POLICIES, make_case("SHOPEE", blocked=True), reply, 50)

    assert result["passed"]
    assert result["raw_violations"] == ["no-other-channel"]
