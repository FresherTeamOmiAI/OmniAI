"""Sinh docs/channel-api/tiktok-dto.md từ các trang API Reference của TikTok Shop đã lưu dạng .md. Owner: TuTM.

    python tools/channel-api/extract_tiktok_dto.py <thư mục docs> [--out docs/channel-api/tiktok-dto.md]

Thư mục docs (không commit, xem tiktok.md mục "Nguồn") có dạng <Nhóm>/<Tên API>.md, mỗi trang gồm
"## Required scope", "## Path", "## Method", "## Query Header", "## Request Body", "## Response Parameters",
"## Error Code". Script chỉ lấy các API trong SELECTED, bỏ code mẫu, rút mô tả về câu đầu.
Sửa SELECTED rồi chạy lại khi cần thêm API.
"""

import argparse
import re
import sys
from pathlib import Path

# (nhóm, tên file không đuôi). Thứ tự = thứ tự trong file sinh ra.
SELECTED = [
    ("Authorization", "Get Authorized Shops"),
    ("Seller", "Get Active Shops"),
    ("Event", "Get Shop Webhooks"),
    ("Event", "Update Shop Webhook"),
    ("Event", "Delete Webhook for an Event"),
    ("Customer Service", "Get Conversations"),
    ("Customer Service", "Get Conversation"),
    ("Customer Service", "Get Conversation Messages"),
    ("Customer Service", "Create Conversation"),
    ("Customer Service", "Send Message"),
    ("Customer Service", "Mark Buyer Messages as Read"),
    ("Customer Service", "Upload Image for Buyer Messages"),
    ("Customer Service", "Get Agent Settings"),
    ("Customer Service", "Update Agent Settings"),
    ("Products", "Search Products"),
    ("Products", "Get Product"),
    ("Products", "Create Product"),
    ("Products", "Edit Product"),
    ("Products", "Partial Edit Product"),
    ("Products", "Update Inventory"),
    ("Products", "Update Price"),
    ("Products", "Inventory Search"),
    ("Products", "Activate Products"),
    ("Products", "Deactivate Products"),
    ("Products", "Delete Products"),
    ("Products", "Recover Products"),
    ("Products", "Get Categories"),
    ("Products", "Get Category Attributes"),
    ("Products", "Get Category Rules"),
    ("Products", "Get Brands"),
    ("Products", "Recommend Category"),
    ("Products", "Upload Product Image"),
    ("Orders", "Get Order List"),
    ("Orders", "Get Order Detail"),
    ("Orders", "Get Order Price Details"),
    ("Promotion", "Create Promotion Activity"),
    ("Promotion", "Update Promotion Activity"),
    ("Promotion", "Get Activity"),
    ("Promotion", "Search Promotion Activities"),
    ("Promotion", "Update Promotion Activity Products"),
    ("Promotion", "Remove Promotion Activity Products"),
    ("Promotion", "Deactivate Activity"),
    ("Promotion", "Republish Activity"),
    ("Promotion", "Get Coupon"),
    ("Promotion", "Search Coupon List"),
    ("Fullfillment", "Search Package"),
    ("Fullfillment", "Get Package Detail"),
    ("Fullfillment", "Ship Package"),
]

# Tham số chung của mọi API, ghi 1 lần ở tiktok.md thay vì lặp lại ở từng API.
COMMON_PARAMS = {"app_key", "sign", "timestamp", "shop_cipher", "content-type", "x-tts-access-token",
                 "code", "message", "request_id", "data"}
ENVELOPE_DEPTH = 1  # trường trong data có 1 dấu ^, bỏ đi 1 cấp cho gọn
DESC_MAX = 160


def sections(text: str) -> dict[str, str]:
    out: dict[str, list[str]] = {}
    current = None
    for line in text.splitlines():
        if m := re.match(r"^## (.+)$", line):
            current = m.group(1).strip()
            out[current] = []
        elif current:
            out[current].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}


def table_rows(block: str) -> list[list[str]]:
    rows = []
    for line in block.splitlines():
        if not line.startswith("| ") or line.startswith("| ---") or line.startswith("| Properties"):
            continue
        rows.append([c.strip() for c in line.strip().strip("|").split(" | ")])
    return rows


def short(desc: str) -> str:
    text = re.sub(r"<br>\s*", " ", desc)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)  # bỏ link, giữ chữ
    text = re.sub(r"\s+", " ", text).strip()
    enums = re.findall(r"`([A-Z][A-Z0-9_]{2,})`", desc) or re.findall(r"- ([A-Z][A-Z0-9_]{2,})\b", desc)
    first = re.split(r"(?<=[.!?])\s", text, maxsplit=1)[0]
    if len(first) > DESC_MAX:
        first = first[:DESC_MAX].rstrip() + "…"
    if enums:
        uniq = list(dict.fromkeys(enums))
        first += " Giá trị: " + ", ".join(uniq[:12]) + (" …" if len(uniq) > 12 else "")
    return first.replace("|", "\\|")


def flatten(rows: list[list[str]], skip_envelope: bool) -> list[tuple[str, str, str, str]]:
    """Đổi cột tên có tiền tố ^ (độ sâu) thành đường dẫn kiểu skus[].inventory[].quantity."""
    stack: list[str] = []
    out = []
    for row in rows:
        if len(row) < 4:
            continue
        raw_name, typ, third, desc = row[0], row[1], row[2], row[3]
        depth = len(raw_name) - len(raw_name.lstrip("^"))
        name = raw_name.lstrip("^")
        if depth == 0 and name in COMMON_PARAMS:
            continue
        if skip_envelope:
            depth -= ENVELOPE_DEPTH
            if depth < 0:
                continue
        stack = stack[:depth]
        path = ".".join(stack + [name])
        stack.append(name + ("[]" if typ.startswith("[]object") else ""))
        out.append((path, typ, third, short(desc)))
    return out


def render_api(group: str, name: str, page: dict[str, str]) -> list[str]:
    method = page.get("Method", "").strip("`")
    path = page.get("Path", "").strip("`")
    scope = page.get("Required scope", "").strip()
    lines = [f"### {name}", "", f"`{method} {path}` · scope: `{scope}`", ""]
    query = flatten(table_rows(page.get("Query Header", "")), skip_envelope=False)
    body = flatten(table_rows(page.get("Request Body", "")), skip_envelope=False)
    resp = flatten(table_rows(page.get("Response Parameters", "")), skip_envelope=True)
    for title, fields, third in (("Query", query, "Bắt buộc"), ("Body", body, "Bắt buộc"),
                                 ("Response `data`", resp, "Ví dụ")):
        if not fields:
            continue
        lines += [f"**{title}**", "", f"| Trường | Kiểu | {third} | Mô tả |", "|---|---|---|---|"]
        for path_, typ, val, desc in fields:
            val = val.replace("|", "\\|")
            if len(val) > 40:
                val = val[:40] + "…"
            lines.append(f"| `{path_}` | {typ} | {val} | {desc} |")
        lines.append("")
    errors = [e for e in table_rows(page.get("Error Code", "")) if e and e[0].isdigit()]
    if errors:
        lines.append("**Mã lỗi:** " + "; ".join(
            f"`{c}` {short(m)}" for c, m, *_ in errors))
        lines.append("")
    return lines


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("docs_dir", type=Path)
    parser.add_argument("--out", type=Path, default=Path("docs/channel-api/tiktok-dto.md"))
    args = parser.parse_args()

    lines = [
        "# TikTok Shop: request/response DTO",
        "",
        "> **File sinh tự động** bằng `tools/channel-api/extract_tiktok_dto.py`, đừng sửa tay.",
        "> Nguồn, ngày lấy, tham số chung và envelope: xem [tiktok.md](tiktok.md).",
        "> Không lặp lại tham số chung (`app_key`, `sign`, `timestamp`, `shop_cipher`, header `x-tts-access-token`)",
        "> và envelope `{code, message, request_id, data}`. Cột Response chỉ liệt kê trường bên trong `data`.",
        "> Tên trường dạng `a[].b`: `a` là mảng object, `b` là trường của mỗi phần tử.",
        "",
    ]
    current_group = None
    missing = []
    for group, name in SELECTED:
        file = args.docs_dir / group / f"{name}.md"
        if not file.exists():
            missing.append(str(file))
            continue
        if group != current_group:
            lines += [f"## {group}", ""]
            current_group = group
        lines += render_api(group, name, sections(file.read_text(encoding="utf-8")))
    # newline="\n": trên Windows write_text mặc định đổi sang CRLF, repo dùng LF (.gitattributes).
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"Đã ghi {args.out} ({len(SELECTED) - len(missing)} API)")
    for m in missing:
        print(f"[thiếu] {m}")
    return 1 if missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
