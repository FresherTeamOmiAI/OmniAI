from bench.policy_check import load_policies, violations

POLICIES = load_policies()


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
