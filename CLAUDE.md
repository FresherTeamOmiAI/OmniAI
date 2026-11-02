# Quy tắc cho Claude Code trong repo OmniAI

## Git
- KHÔNG thêm dòng `Co-Authored-By: Claude ...` (hay bất kỳ attribution nào của Claude/AI) vào commit message.
- KHÔNG thêm dòng "Generated with Claude Code" vào mô tả Pull Request.
- Commit theo Conventional Commits, kèm mã task trong file tracking, ví dụ: `feat(channel): telegram webhook adapter [T-17.1]`.
- Không commit `.env`, API key, thư mục `sample/`.
