# Quy tắc cho Claude Code trong repo OmniAI

## Git
- KHÔNG thêm dòng `Co-Authored-By: Claude ...` (hay bất kỳ attribution nào của Claude/AI) vào commit message.
- KHÔNG thêm dòng "Generated with Claude Code" vào mô tả Pull Request.
- Commit theo Conventional Commits, kèm mã task trong file tracking, ví dụ: `feat(channel): shopee simulator adapter [T-21.1]`.
- Không commit `.env`, API key, thư mục `sample/`.

## Specs: đọc trước, cập nhật sau (`docs/specs/`)
1. **Đầu mỗi task:** đọc `docs/specs/README.md`, sau đó chỉ đọc spec liên quan (theo bảng "Đọc gì theo loại task") cùng `status.md`. Đừng quét cả repo nếu spec đã trả lời được.
2. **Cần chi tiết code:** nếu có MCP `codebase-memory` thì dùng `search_graph`, `get_code_snippet`, `trace_path` (project `D-FsoftAcademy-OmniAI`) trước Grep hoặc Read cả file. Chỉ Read đầy đủ những file sắp sửa. Nếu kết quả có vẻ cũ thì chạy `index_repository` (mode `fast`).
3. **Spec mâu thuẫn với code:** code là chuẩn. Sửa spec ngay trong thay đổi đang làm.
4. **Sau khi sửa code:** nếu đụng tới API/DTO, DB/migration, YAML luật, cấu trúc thư mục/package, env/cổng/cách chạy/script `.bat`, hoặc trạng thái tính năng, thì cập nhật spec tương ứng, `docs/specs/status.md` và dòng `> Cập nhật:` **trong cùng commit/PR**. Refactor nội bộ không đổi hành vi, sửa bug không đổi hợp đồng, hay chỉ thêm test thì không cần.
5. **Câu trả lời cuối** ghi rõ spec nào đã cập nhật, hoặc "specs: không cần cập nhật" kèm lý do ngắn.
- Stop hook `tools/agent/specs_guard.py` sẽ nhắc nếu code đã đổi mà `docs/specs/` chưa đổi.
