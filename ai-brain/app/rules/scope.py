"""Chặn câu hỏi ngoài phạm vi shop mỹ phẩm trước khi gọi LLM.

Luật nằm ở shared/policies/scope/question.yaml. Khớp một luật refuse thì từ chối.
Không khớp luật allow nào thì cũng từ chối. Câu được cho qua mới tới tool hoặc model.
"""

from functools import lru_cache
from pathlib import Path
import re

import yaml

_POLICY_PATH = Path(__file__).resolve().parents[3] / "shared" / "policies" / "scope" / "question.yaml"


class ScopeDecision:
    def __init__(self, rule_id: str, message: str) -> None:
        self.rule_id = rule_id
        self.message = message


@lru_cache(maxsize=1)
def _policy() -> tuple[str, tuple[tuple[str, re.Pattern[str]], ...], tuple[re.Pattern[str], ...]]:
    doc = yaml.safe_load(_POLICY_PATH.read_text(encoding="utf-8"))
    fallback = str(doc["fallback_message"]).strip()
    refuse = tuple(
        (str(rule["id"]), re.compile(str(rule["pattern"]), re.IGNORECASE))
        for rule in doc.get("refuse") or []
    )
    allow = tuple(re.compile(str(rule["pattern"]), re.IGNORECASE) for rule in doc.get("allow") or [])
    if not fallback or not allow:
        raise RuntimeError(f"Policy phạm vi thiếu fallback hoặc allow: {_POLICY_PATH}")
    return fallback, refuse, allow


def scope_block(text: str) -> ScopeDecision | None:
    """None nếu câu được tư vấn. Ngược lại là câu từ chối và id luật khớp."""
    fallback, refuse, allow = _policy()
    for rule_id, pattern in refuse:
        if pattern.search(text):
            return ScopeDecision(rule_id, fallback)
    if any(pattern.search(text) for pattern in allow):
        return None
    return ScopeDecision("outside-shop", fallback)
