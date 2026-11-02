from app.rules.ingredients import check_routine, detect_groups


def test_detects_groups_from_inci():
    assert detect_groups("Aqua, Retinol, Glycolic Acid") == {"retinoid", "aha"}


def test_retinoid_with_bha_conflicts():
    codes = [w.code for w in check_routine("Retinol, Salicylic Acid")]
    assert codes == ["conflict:bha+retinoid"]


def test_no_conflict_for_gentle_routine():
    assert check_routine("Aqua, Glycerin, Ceramide NP, Niacinamide") == []


def test_pregnancy_warning_only_when_pregnant():
    assert check_routine("Retinol") == []
    assert [w.code for w in check_routine("Retinol", pregnant=True)] == ["pregnancy:retinoid"]
