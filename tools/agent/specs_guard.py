"""Stop hook cho Claude Code: nhắc agent cập nhật docs/specs khi code đã đổi mà specs chưa đổi.

Khai báo trong .claude/settings.json (hooks.Stop). Đọc JSON của hook từ stdin:
- stop_hook_active = true (agent đang chạy tiếp do chính hook này) -> cho dừng, tránh lặp vô hạn.
- Ngược lại: nếu `git status` có thay đổi trong thư mục code nhưng không có thay đổi trong docs/specs
  -> in {"decision": "block", "reason": ...} để agent cập nhật specs (hoặc giải thích vì sao không cần).

Chạy tay để thử:  echo {} | python tools/agent/specs_guard.py
"""

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CODE_PREFIXES = (
    "backend/src/",
    "backend/pom.xml",
    "ai-brain/app/",
    "ai-brain/requirements.txt",
    "web/src/",
    "web/vite.config.ts",
    "web/package.json",
    "bench/bench/",
    "bench/datasets/",
    "shared/",
    "docker-compose.yml",
    ".env.example",
    "tools/systembat/",
)
SPECS_PREFIX = "docs/specs/"

REASON = (
    "Code đã thay đổi ({files}) nhưng docs/specs chưa được cập nhật. "
    "Theo CLAUDE.md: nếu thay đổi chạm API, DB, YAML luật, cấu trúc thư mục, env, cách chạy hoặc trạng thái tính năng "
    "thì cập nhật spec tương ứng + docs/specs/status.md + dòng 'Cập nhật'. "
    "Nếu không cần (refactor nội bộ, chỉ sửa test, thay đổi không phải của phiên này) thì nói ngắn gọn lý do rồi kết thúc."
)


def changed_files() -> list[str]:
    out = subprocess.run(
        ["git", "-C", str(ROOT), "status", "--porcelain", "--untracked-files=all"],
        capture_output=True, text=True, encoding="utf-8",
    )
    if out.returncode != 0:
        return []
    # Dòng porcelain: "XY path" hoặc "XY old -> new"
    return [line[3:].split(" -> ")[-1].strip('"') for line in out.stdout.splitlines() if len(line) > 3]


def main() -> int:
    try:
        payload = json.loads(sys.stdin.read() or "{}")
    except json.JSONDecodeError:
        payload = {}
    if payload.get("stop_hook_active"):
        return 0

    files = changed_files()
    code = [f for f in files if f.startswith(CODE_PREFIXES)]
    specs_touched = any(f.startswith(SPECS_PREFIX) for f in files)
    if code and not specs_touched:
        shown = ", ".join(code[:5]) + (f" và {len(code) - 5} file khác" if len(code) > 5 else "")
        # ensure_ascii: stdout của hook là pipe, trên Windows mặc định cp1252 không in được tiếng Việt
        print(json.dumps({"decision": "block", "reason": REASON.format(files=shown)}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
