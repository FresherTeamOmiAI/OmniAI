import pytest
import yaml

from bench.policy_check import DEFAULT_POLICY_DIR, load_policies, violations

POLICIES = load_policies()
# Cùng bộ ca với PolicyEngineTest bên backend: chứng minh regex chạy giống nhau ở Java và Python.
SHARED_CASES = yaml.safe_load((DEFAULT_POLICY_DIR / "tests" / "cases.yaml").read_text(encoding="utf-8"))["cases"]


def test_loads_every_channel():
    assert set(POLICIES) == {"WEB", "TELEGRAM", "MESSENGER", "SHOPEE", "TIKTOK"}


def test_shopee_flags_link_phone_and_other_channel():
    text = "Mua trên website https://omni-demo.local hoặc gọi 0901234567"
    assert set(violations(POLICIES, "SHOPEE", text)) == {"no-external-link", "no-phone", "no-other-channel"}


def test_web_allows_link():
    assert violations(POLICIES, "WEB", "Xem tại https://omni-demo.local") == []


def test_medical_claim_flagged_everywhere():
    for channel in POLICIES:
        assert "no-medical-claim" in violations(POLICIES, channel, "Sản phẩm đặc trị mụn")


@pytest.mark.parametrize("channel", sorted(POLICIES))
@pytest.mark.parametrize("case", SHARED_CASES, ids=[c["id"] for c in SHARED_CASES])
def test_shared_case(case, channel):
    matches = case.get("matches") or {}
    expected = set(matches.get("*", [])) | set(matches.get(channel, []))
    assert set(violations(POLICIES, channel, case["text"])) == expected
