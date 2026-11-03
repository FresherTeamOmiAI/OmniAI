# Quyết định kỹ thuật

> Cập nhật: 2026-11-03 · commit c88bfc6

Mỗi quyết định ghi 1–3 dòng: quyết định gì, vì sao, đổi lại mất gì. Thêm dòng mới ở cuối, không xóa dòng cũ (nếu quyết định bị thay thì ghi "thay bởi #n").

| # | Ngày | Quyết định | Lý do / đánh đổi |
|---|---|---|---|
| 1 | 2026-11-02 | Backend là **modular monolith** Spring Boot, mỗi module một package | Team 4 người, mạnh Java. Tách service sau nếu cần. |
| 2 | 2026-11-02 | AI Brain là **service Python riêng** (FastAPI), gọi qua HTTP `/chat` | Hệ sinh thái AI (LangGraph, embedding) ở Python. Đổi lại có thêm 1 hợp đồng phải giữ đồng bộ (`schemas.py` ↔ `BrainRequest/Response`). |
| 3 | 2026-11-02 | Luật kênh để trong **YAML dùng chung** (`shared/policies`) cho cả Java và bench Python | Bench chấm độc lập bằng cùng luật. Đổi lại regex phải tương thích cả hai engine. |
| 4 | 2026-11-02 | **FakeProvider** là mặc định, cố tình trả lời vi phạm | Dev, test và bench chạy offline, không tốn tiền API, và policy luôn có cái để chặn. |
| 5 | 2026-11-02 | Kênh sàn đi qua **simulator** cho tới khi spike S0 (US-03) kết luận được | Sandbox sàn có thể không hỗ trợ chat API. |
| 6 | 2026-11-02 | **PostgreSQL + Testcontainers** thay H2 (commit 9daa84a) | Cần pgvector và hành vi giống production. Đổi lại test backend bắt buộc có Docker. |
| 7 | 2026-11-02 | Schema DB do **Flyway** quản lý, JPA `validate` | Kiểm soát migration rõ ràng. Không sửa migration đã merge. |
| 8 | 2026-11-02 | Web tổ chức **modular theo feature** (commit 88ebce8) | Mỗi người làm UI phần mình, ít conflict. |
| 9 | 2026-11-02 | Liên kết danh tính **chỉ khi có bằng chứng + consent**, không tự gộp | Luật BVDLCN 91/2025/QH15. |
| 10 | 2026-11-03 | Thêm **docs/specs** + rule trong CLAUDE.md + Stop hook | Giảm số lần agent đọc lại code. Đổi lại phải cập nhật spec khi đổi hợp đồng. |
| 11 | 2026-11-03 | Script chạy Windows trong `tools/systembat`, nạp `.env` hộ ứng dụng | Không phải gõ lệnh, cấu hình giống nhau giữa các máy. |
