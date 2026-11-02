"""Luật thành phần TẤT ĐỊNH (không để LLM tự quyết). LLM chỉ diễn giải kết quả.

BẢN MẪU để minh họa cấu trúc. Danh sách nhóm hoạt chất và luật cần người có chuyên môn
(dược sĩ / bác sĩ da liễu) review trước khi dùng cho khách thật.
"""

from dataclasses import dataclass

INGREDIENT_GROUPS: dict[str, tuple[str, ...]] = {
    "retinoid": ("retinol", "retinal", "retinaldehyde", "tretinoin", "adapalene", "retinyl palmitate"),
    "aha": ("glycolic acid", "lactic acid", "mandelic acid", "aha"),
    "bha": ("salicylic acid", "bha"),
    "bpo": ("benzoyl peroxide",),
    "vitamin_c": ("ascorbic acid", "vitamin c"),
}

# Cặp nhóm không nên dùng cùng một routine (cùng buổi).
CONFLICTS: dict[frozenset[str], str] = {
    frozenset({"retinoid", "aha"}): "Retinoid và AHA cùng buổi dễ gây kích ứng, nên tách sáng/tối hoặc xen kẽ ngày.",
    frozenset({"retinoid", "bha"}): "Retinoid và BHA cùng buổi dễ gây khô, kích ứng, nên tách sáng/tối hoặc xen kẽ ngày.",
    frozenset({"retinoid", "bpo"}): "Benzoyl peroxide có thể làm giảm hiệu quả một số retinoid, không nên dùng cùng lúc.",
}

# Nhóm cần tránh / hỏi ý kiến bác sĩ khi mang thai hoặc cho con bú.
PREGNANCY_AVOID: dict[str, str] = {
    "retinoid": "Nhóm retinoid thường được khuyên tránh khi mang thai/cho con bú, bạn nên hỏi ý kiến bác sĩ.",
}


@dataclass(frozen=True)
class RuleWarning:
    code: str
    message: str


def detect_groups(text: str) -> set[str]:
    lowered = text.lower()
    return {group for group, names in INGREDIENT_GROUPS.items() if any(n in lowered for n in names)}


def check_routine(ingredients_text: str, pregnant: bool = False) -> list[RuleWarning]:
    """Kiểm tra danh sách thành phần (INCI hoặc tên nhóm) của 1 routine."""
    groups = detect_groups(ingredients_text)
    warnings = [
        RuleWarning(code="conflict:" + "+".join(sorted(pair)), message=msg)
        for pair, msg in CONFLICTS.items()
        if pair <= groups
    ]
    if pregnant:
        warnings += [
            RuleWarning(code=f"pregnancy:{group}", message=msg)
            for group, msg in PREGNANCY_AVOID.items()
            if group in groups
        ]
    return warnings
