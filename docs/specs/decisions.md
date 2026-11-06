# Quyết định kỹ thuật

> Cập nhật: 2026-11-06 · commit 500c154

Mỗi quyết định ghi 1–3 dòng: quyết định gì, vì sao, đổi lại mất gì. Thêm dòng mới ở cuối, không xóa dòng cũ (nếu quyết định bị thay thì ghi "thay bởi #n").

| # | Ngày | Quyết định | Lý do / đánh đổi |
|---|---|---|---|
| 1 | 2026-11-02 | Backend là **modular monolith** Spring Boot, mỗi module một package | Team 4 người, mạnh Java. Tách service sau nếu cần. |
| 2 | 2026-11-02 | AI Brain là **service Python riêng** (FastAPI), gọi qua HTTP `/chat` | Hệ sinh thái AI (LangGraph, embedding) ở Python. Đổi lại có thêm 1 hợp đồng phải giữ đồng bộ (`schemas.py` ↔ `BrainRequest/Response`). |
| 3 | 2026-11-02 | Luật kênh để trong **YAML dùng chung** (`shared/policies`) cho cả Java và bench Python | Bench chấm độc lập bằng cùng luật. Đổi lại regex phải tương thích cả hai engine. |
| 4 | 2026-11-02 | **FakeProvider** là mặc định, cố tình trả lời vi phạm | Dev, test và bench chạy offline, không tốn tiền API, và policy luôn có cái để chặn. |
| 5 | 2026-11-02 | Kênh sàn đi qua **simulator** cho tới khi spike S0 (US-03) kết luận được | Sandbox sàn có thể không hỗ trợ chat API. Thay bởi #12. |
| 6 | 2026-11-02 | **PostgreSQL + Testcontainers** thay H2 (commit 9daa84a) | Cần pgvector và hành vi giống production. Đổi lại test backend bắt buộc có Docker. |
| 7 | 2026-11-02 | Schema DB do **Flyway** quản lý, JPA `validate` | Kiểm soát migration rõ ràng. Không sửa migration đã merge. |
| 8 | 2026-11-02 | Web tổ chức **modular theo feature** (commit 88ebce8) | Mỗi người làm UI phần mình, ít conflict. |
| 9 | 2026-11-02 | Liên kết danh tính **chỉ khi có bằng chứng + consent**, không tự gộp | Luật BVDLCN 91/2025/QH15. |
| 10 | 2026-11-03 | Thêm **docs/specs** + rule trong CLAUDE.md + Stop hook | Giảm số lần agent đọc lại code. Đổi lại phải cập nhật spec khi đổi hợp đồng. |
| 11 | 2026-11-03 | Script chạy Windows trong `tools/systembat`, nạp `.env` hộ ứng dụng | Không phải gõ lệnh, cấu hình giống nhau giữa các máy. |
| 12 | 2026-11-04 | Shopee và TikTok Shop đi qua **simulator theo schema thật** (định nghĩa ở [backend.md](backend.md), mục "Kênh sàn"). Adapter thật ở trạng thái *pending* vì 2 sàn chưa duyệt app, dù TuTM đã đăng ký và thử (T-03.3) | Không phụ thuộc lịch duyệt của sàn. Khi sàn duyệt, gửi/nhận thử 1 tin trên shop test: đạt trước S5 (Shopee, 08/12) hoặc S6 (TikTok, 15/12) thì bật chế độ live cho sàn đó. Không kịp thì demo sàn là giả lập, ghi rõ trong báo cáo. |
| 13 | 2026-11-04 | AI Brain chỉ dùng **LLM miễn phí** (free tier API hoặc model mở chạy local). Model cụ thể chốt khi duyệt báo cáo US-05 theo tiêu chí trong `docs/llm-eval/README.md` (T-05.3) | Chi phí 0đ. Đổi lại phụ thuộc quota và điều khoản của free tier, có thể chậm hơn hoặc kém tiếng Việt hơn model trả phí, nên luôn cần 1 model dự phòng của nhà cung cấp khác và chỉ gửi dữ liệu tổng hợp. |
| 14 | 2026-11-06 | **Tạm chọn LLM** (T-05.3, duyệt có điều kiện): model chính `LLM_PROVIDER=gemini`, `LLM_MODEL=gemini-3.6-flash` (Google AI Studio free tier); dự phòng `LLM_PROVIDER=groq`, `LLM_MODEL=qwen/qwen3.8-27b`. Chi tiết ở `docs/llm-eval/report.md`, mục "Kết luận duyệt" | Gemini là model duy nhất qua G1–G3 (không bịa giá, không khuyên retinol khi mang thai). Đổi lại p95 7,65 s vượt NFR-01 (5 s), chưa chứng minh G5 (quota) và G6 (gọi tool). Điều kiện: (1) T-06.4 tách token suy nghĩ khỏi `max_tokens`, đo lại p95 trước Sprint Review S1 (16/11), vẫn > 5 s thì PO chọn nới NFR-01 hoặc đổi model chính sang qwen; (2) có log gọi tool thật trước S3; (3) 50 request liên tiếp không bị 429 trước bench 50 ca ở S2; (4) chỉ bật qwen khi đã có T-06.6. |
| 15 | 2026-11-06 | Nhánh mặc định trên GitHub là **`dev`**. Ruleset "Protect main and dev branches." ghi rõ cả `refs/heads/main` và `refs/heads/dev` (chặn xóa, chặn force push, PR cần 1 approve) | PR mới tự vào `dev`, và `Closes #n` tự đóng issue khi merge vào `dev` (trước đây không đóng vì nhánh mặc định là `main`). Đổi lại `main` chỉ được cập nhật khi phát hành cuối sprint. |
| 16 | 2026-11-06 | **Không có task review riêng** trong backlog. Mỗi PR cần 1 approve của thành viên bất kỳ, không gán reviewer cố định. SRS và tài liệu không cần ký duyệt: PO quyết định, ai thấy chỗ sai thì comment hoặc mở PR, có vấn đề thì cả team họp | Review chéo và ký duyệt ở S0 làm chậm tiến độ. Đổi lại chất lượng dựa vào kết quả test/bench dán trong PR và việc PO nghiệm thu US cuối sprint. |
| 17 | 2026-11-06 | Thầy hướng dẫn **chấp nhận** kênh sàn chạy bằng simulator theo schema thật (SRS Q-08). Xác nhận #12 | Demo và bench kênh Shopee/TikTok không còn phụ thuộc việc sàn duyệt app. Adapter thật vẫn là tùy chọn khi sàn duyệt kịp. |
