"""Kiểm tương phản WCAG cho token màu trong web/src/shared/ui/tokens.css (T-56.1).

Chạy từ gốc repo: python tools/web/check_contrast.py web/src/shared/ui/tokens.css
Chữ thường cần >= 4.5:1; viền ô nhập, viền chip, vòng focus cần >= 3:1 (WCAG 1.4.11).
"""

import re
import sys

PAIRS = [
    # (chữ/viền, nền, ngưỡng, mô tả)
    ("--color-text", "--color-bg", 4.5, "chữ chính trên nền trang"),
    ("--color-text", "--color-surface", 4.5, "chữ chính trên thẻ"),
    ("--color-heading", "--color-bg", 4.5, "tiêu đề trên nền trang"),
    ("--color-heading", "--color-surface", 4.5, "tiêu đề trên thẻ"),
    ("--color-muted", "--color-bg", 4.5, "chữ phụ trên nền trang"),
    ("--color-muted", "--color-surface", 4.5, "chữ phụ trên thẻ"),
    ("--color-muted", "--color-surface-2", 4.5, "chữ phụ trên nền phụ"),
    ("--color-primary", "--color-surface", 4.5, "link/giá màu chính trên thẻ"),
    ("--color-primary", "--color-bg", 4.5, "link màu chính trên nền trang"),
    ("--color-primary", "--color-primary-soft", 4.5, "chip đang chọn"),
    ("--color-on-primary", "--color-primary", 4.5, "chữ trên nút chính"),
    ("--color-on-primary", "--color-primary-hover", 4.5, "chữ trên nút chính khi hover"),
    ("--color-accent", "--color-accent-soft", 4.5, "chữ AI trên nền tím nhạt"),
    ("--color-text", "--color-accent-soft", 4.5, "chữ thường trong bong bóng bot"),
    ("--color-success", "--color-success-soft", 4.5, "hộp đặt hàng thành công"),
    ("--color-warning", "--color-warning-soft", 4.5, "banner thử nghiệm / cảnh báo"),
    ("--color-danger", "--color-surface", 4.5, "lỗi dưới ô nhập"),
    ("--color-danger", "--color-danger-soft", 4.5, "hộp lỗi"),
    ("--color-border-strong", "--color-surface", 3.0, "viền ô nhập trên thẻ"),
    ("--color-border-strong", "--color-bg", 3.0, "viền chip trên nền trang"),
    ("--color-focus", "--color-surface", 3.0, "vòng focus trên thẻ"),
    ("--color-focus", "--color-bg", 3.0, "vòng focus trên nền trang"),
]


def luminance(hex_color: str) -> float:
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def channel(c: float) -> float:
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def ratio(a: str, b: str) -> float:
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    css = open(sys.argv[1], encoding="utf-8").read()
    tokens = dict(re.findall(r"(--color-[a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;", css))
    failed = 0
    for fg, bg, need, label in PAIRS:
        if fg not in tokens or bg not in tokens:
            print(f"THIẾU  {fg} / {bg}")
            failed += 1
            continue
        r = ratio(tokens[fg], tokens[bg])
        ok = r >= need
        failed += not ok
        print(f"{'ĐẠT ' if ok else 'TRƯỢT'} {r:5.2f}:1 (cần {need}) {label}: {fg} {tokens[fg]} / {bg} {tokens[bg]}")
    print(f"\n{len(PAIRS) - failed}/{len(PAIRS)} cặp đạt")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
