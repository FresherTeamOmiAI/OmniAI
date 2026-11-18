import pytest

from perf.load_test import percentile, summarize


def test_percentile_uses_nearest_rank():
    values = [float(v) for v in range(1, 21)]  # 1..20
    assert percentile(values, 50) == 10
    assert percentile(values, 95) == 19
    assert percentile(values, 100) == 20
    assert percentile([5.0], 95) == 5


def test_percentile_rejects_bad_input():
    with pytest.raises(ValueError):
        percentile([], 50)
    with pytest.raises(ValueError):
        percentile([1.0], 0)


def test_summarize_ignores_failed_samples_in_latency():
    samples = [(10.0, 2048, True), (20.0, 2048, True), (999.0, 0, False)]
    result = summarize("store_list", samples)
    assert result.requests == 3
    assert result.errors == 1
    assert result.max_ms == 20.0
    assert result.mean_kb == 2.0
