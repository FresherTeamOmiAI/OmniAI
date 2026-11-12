"""Đọc cùng bộ luật shared/policies/*.yaml với backend để chấm độc lập câu trả lời cuối.

Backend lọc câu trả lời, bench kiểm tra lại: nếu câu khách nhận vẫn khớp luật nào thì đó là lỗ hổng.
"""

import re
from dataclasses import dataclass
from pathlib import Path

import yaml

DEFAULT_POLICY_DIR = Path(__file__).resolve().parents[2] / "shared" / "policies"


@dataclass(frozen=True)
class Rule:
    id: str
    pattern: re.Pattern[str]
    action: str


def load_policies(policy_dir: Path = DEFAULT_POLICY_DIR) -> dict[str, list[Rule]]:
    docs = [yaml.safe_load(p.read_text(encoding="utf-8")) for p in sorted(policy_dir.glob("*.yaml"))]
    common = [rule for d in docs if d["channel"] == "*" for rule in _rules(d)]
    return {d["channel"]: _rules(d) + common for d in docs if d["channel"] != "*"}


def _rules(doc: dict) -> list[Rule]:
    return [
        Rule(id=r["id"], pattern=re.compile(r["pattern"], re.IGNORECASE), action=r["action"].lower())
        for r in doc.get("rules") or []
    ]


def violations(policies: dict[str, list[Rule]], channel: str, text: str) -> list[str]:
    """Danh sách id luật mà câu trả lời cuối vẫn vi phạm (mong đợi: rỗng)."""
    return [rule.id for rule in policies[channel] if rule.pattern.search(text)]


def load_fallbacks(policy_dir: Path = DEFAULT_POLICY_DIR) -> dict[str, str]:
    """fallback_message của từng kênh: khách nhận đúng câu này nghĩa là policy đã chặn cả câu (blocked)."""
    docs = [yaml.safe_load(p.read_text(encoding="utf-8")) for p in sorted(policy_dir.glob("*.yaml"))]
    return {d["channel"]: (d.get("fallback_message") or "").strip() for d in docs if d["channel"] != "*"}
