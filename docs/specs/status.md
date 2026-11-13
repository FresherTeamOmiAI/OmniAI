# Trạng thái hiện tại

> Cập nhật: 2026-11-13 · T-04.1 tách giá theo kênh khỏi `product` (V13, decisions #32); 2026-11-12 · T-09.1 store phân trang 20 SKU/trang (chưa có trang chi tiết); US-11 lưu hội thoại mọi kênh (T-11.1–T-11.3); T-47.1 catalog thật 366 SKU trong DB, 366 ảnh đã lên Supabase (decisions #27, #30); US-25 kênh TikTok Shop qua simulator (T-25.1–T-25.3), chạy thử với Gemini + Postman; trước đó: T-06.8 timeout widget, lập lại kế hoạch (decisions #22–#24) · Sprint hiện tại: **S2 (10–16/11/2026)**

Bảng này cho biết cái gì đã chạy được, cái gì mới là khung, cái gì chưa có. Agent đọc file này **trước khi** đi tìm code để khỏi tìm thứ chưa tồn tại. Kế hoạch chi tiết: `tools/tracking/backlog.py` và GitHub Issues (milestone Sprint 0–12).

Chú thích: **Done** = chạy được, có test · **Khung** = có code tối thiểu/stub, cần làm tiếp · **Chưa** = chưa có code.

## Theo module

| Phần | Trạng thái | Ghi chú | US |
|---|---|---|---|
| Docker (Postgres+pgvector, Redis, RabbitMQ) | Done | Redis và RabbitMQ chưa được dùng | |
| Script chạy Windows `tools/systembat` | Done | setup / run-all / stop-all / test-all / bench | US-02 |
| CanonicalMessage + ChannelAdapter | Done | | |
| MessageRouter + idempotency | Khung | Idempotency in-memory (US-29). Đã lưu hội thoại | US-06, US-11, US-29 |
| Lưu hội thoại mọi kênh | Done | `conversation/*`, bảng V11 (decisions #31). `MessageRouter` lưu tin khách trước khi gọi Brain, lưu câu bot kèm rawText/finalText/vi phạm/blocked/handoff. Brain lỗi hay ném exception thì vẫn lưu, `handoff=true`. API `GET /api/conversations` (lọc kênh, phân trang) và `/{id}/messages`, cần Basic ADMIN. Test `ConversationStoreIT` (gồm T-11.3 Brain lỗi), `ConversationApiTest`, TC tay `docs/testcases/US-11.md`. Chưa có màn admin (US-14, S3) | US-11 |
| Kênh WEB (WebSocket) | Done | T-06.1 FE widget auto-reconnect + T-06.2 BE adapter + T-06.8: server trả `invalid_message` cho tin JSON lỗi/rỗng, widget timeout 12 s, test WS thật `WebChatSocketIT` | US-06 |
| Simulator API | Done | Dùng cho bench và `/admin/policy` | US-07 |
| Kênh Telegram / Messenger | Đã bỏ | Bỏ khỏi dự án ngày 11/11 (decisions #22): xóa enum, YAML; hủy US-17, US-18 | |
| Kênh TikTok Shop (simulator theo schema thật) | Done (sim) | App riêng `simulators/tiktok-shop` :8090 (decisions #28): OAuth2, Event, Customer Service, chữ ký request/webhook, rate limit 50 đồng thời, phân trang, fault injection `/sim/*`. Hub `channel.tiktok`: webhook kiểm chữ ký, trả 200 rồi xử lý async, chống trùng, token lưu DB (V8, decisions #29), tự refresh. Bảng đối chiếu `docs/channel-api/tiktok.md` (T-25.3). Token/sign/webhook/mã lỗi chung **chưa đối chiếu** docv2. Bench `tiktok-flow.jsonl` với Gemini: 3/5 (2 ca chỉ chậm > 5 s, 0 lọt vi phạm). Postman `backend/http/OmniAI-tiktok.postman_collection.json` 50/50 assertion, TC ở `docs/testcases/US-25.md` | US-25 |
| Kênh Shopee | Chưa | Đã có YAML luật. 100% simulator theo schema thật (decisions #22), ChienPX làm ở S2 | US-21 |
| Đồng bộ hub ↔ kênh (trụ E) | Chưa | Đi: sản phẩm, giá, tồn kho, khuyến mãi, trạng thái đơn. Về: đơn hàng, duyệt sản phẩm, đánh giá (hội thoại CSKH ở US-11, 14). Bắt đầu S3, bảng US ở [backend.md](backend.md) | US-49–55 |
| Dữ liệu sản phẩm thật kèm ảnh | Done | Catalog trong DB là **366 SKU thật** của 50 hãng có bán ở VN, từ Open Beauty Facts (V9 xoá 80 SKU hư cấu, V10 nạp; decisions #27, #30). 366 ảnh đã lên Supabase (bucket public `product-images`), API trả `imageUrl`, store hiện ảnh và ghi nguồn. Còn lại: bench/llm-eval còn tham chiếu hãng hư cấu, tên sản phẩm `auto` có thể là tiếng Pháp/Đức/Thổ. [Chi tiết](../catalog/du-lieu-san-pham-that.md) | US-47 |
| PolicyEngine + YAML | Done (v0) | YAML sàn là bản nháp | US-07, US-20 |
| AI Brain `/chat` + LLMProvider | Khung | Chỉ chạy LLM thật: `LLM_PROVIDER` là `gemini` (mặc định) hoặc `groq`, đã bỏ `fake` (T-12.4, decisions #25). Thiếu `LLM_API_KEY` hoặc `CHATBOT_DB_URL` thì không khởi động. pytest dùng stub trong `ai-brain/tests/`. `gemini-3.6-flash` trả lời đúng ngữ cảnh 10/10 câu mẫu (T-06.4), p95 6,7 s > 5 s, PO chấp nhận ở S1 (decisions #21). LLM lỗi trả 502 `llm_unavailable`, câu trả lời là văn bản thuần không markdown (T-06.7). Tên và giá niêm yết lấy từ view `chatbot_catalog` bằng role `chatbot_reader` | US-05, US-06 |
| AI Brain: chặn số tự bịa (`rules/grounding.py`) | Done | Giữ giá khi khớp `chatbot_catalog`. Vẫn bỏ tồn kho, khuyến mãi và giá model tự bịa | US-06 (T-06.6), US-15 |
| Luật thành phần | Khung | Bản mẫu, cần chuyên gia review | US-27 |
| RAG / tool / agent loop | Đang làm | search_product + embedding pgvector (US-10). Chưa có agent loop | US-10, US-15 |
| Catalog: GET list/detail | Done | 366 SKU thật trong DB (364 active), giá trả dạng `prices: {WEB, SHOPEE, TIKTOK}` | |
| Catalog: schema đợt 1 (V4) | Done | Thêm mô tả, vấn đề da, buổi dùng; NOT NULL + CHECK. Cột embedding ở V12 (US-10). **Giá theo kênh tách ra `sales_channel` + `product_channel` (V13, sửa theo review, decisions #32)**: thêm sàn không phải sửa bảng `product`. Chưa tách: brand, category, loại da, INCI (task sau) | US-04 |
| Catalog: file 80 SKU (`shared/catalog/products.csv`) | Done | Có validator `tools/catalog/validate_catalog.py`. Đã nạp vào DB qua migration V5, sinh bằng `tools/catalog/csv_to_sql.py` (T-08.2) | US-04, US-08 |
| Catalog: API admin `/api/admin/products` | Done | List (gồm SKU ẩn), tạo, sửa, ẩn/hiện, validate giống validator CSV, lỗi ProblemDetail (T-08.1) | US-08 |
| Màn admin sản phẩm `/admin/products` | Done | Đăng nhập Basic, bảng gồm SKU ẩn + tìm/lọc/phân trang, thêm/sửa trong dialog, ẩn/hiện (T-08.3). Chưa có test tự động phía web, TC trình duyệt ở `docs/testcases/US-08.md` mục D | US-08 |
| Xác thực admin | Khung | HTTP Basic 1 tài khoản từ env cho `/api/admin/**` (SRS Q-01). Tài khoản thật ở US-13 | US-08, US-13 |
| Customer / ChannelIdentity entity | Khung | Chưa có API | |
| Identity (OTP, OMNI-code) | Chưa | Mới có package-info | US-13, 22 |
| Order, Promotion | Chưa | Mới có package-info | US-09, 33 |
| Web store (list phân trang + lọc loại da) | Khung | Chưa có trang chi tiết, chưa đặt hàng ảo | US-09 |
| Trang thử luật kênh `/admin/policy` | Done | | UC-24 |
| Bench runner + policy_check | Done | 11 ca mẫu qua simulator + 6 ca luồng web qua WebSocket (`transport: ws`). Luôn chạy trên Gemini + Postgres thật; kỳ vọng không còn phụ thuộc câu của FakeProvider (T-12.4) | US-06, US-12 |
| CI | Chưa | | US-16 |
| docs/specs + rule cho agent | Done | Stop hook `tools/agent/specs_guard.py` | |

## Lỗ hổng / nợ kỹ thuật đã biết
- Idempotency chỉ nằm trong bộ nhớ, sẽ mất khi restart và sai khi chạy nhiều instance (S7 chuyển Redis). Chống trùng webhook TikTok (`tts_notification_id`, `message_id`) cũng vậy.
- TikTok: thuật toán ký, chữ ký webhook, số `type` webhook, API token và mã lỗi 105xxx/106xxx/36009004/36009006 là giả định, chờ đối chiếu docv2 (docs/channel-api/tiktok.md mục 1). Token trong `channel_credential` lưu dạng rõ.
- Webhook TikTok lỗi sau khi đã trả 200 thì chỉ log, chưa có dead-letter (US-29).
- Hội thoại: DB lỗi lúc lưu thì chỉ log, mất bản ghi lượt đó. TikTok báo `45101006` thì hub gửi `fallback_message` nhưng DB vẫn ghi `finalText` ban đầu. Chưa có xóa/ẩn dữ liệu hội thoại theo yêu cầu khách (BVDLCN, US-30).
- Backend/ai-brain không tự đọc `.env` (script `.bat` nạp hộ, IDE thì phải tự cấu hình).
- Chưa có `mvnw`, mỗi máy phải tự cài Maven.
- Widget chat: nối lại WebSocket thì `channelUserId` đổi theo session mới (tài khoản web ở US-13).
- Câu Gemini có markdown (`**`), widget hiện thô (T-06.7).
- Bench tính ca chậm hơn 5 s là fail, nên tỉ lệ pass với Gemini lẫn cả lỗi chất lượng và lỗi độ trễ (decisions #21). Chưa tách thành metric riêng.
- Chạy brain và bench cần `LLM_API_KEY` và quota free tier; CI (US-16) sẽ phải giải quyết việc cấp key.

## Sprint S0–S2 (tóm tắt)
- **S0** (03–09/11): SRS 1.0, US, UC (HungDQ; LucVT, ChienPX góp ý; không ký duyệt, decisions #16) · setup môi trường (cả 4) · spike chat API Shopee/TikTok (TuTM) · schema + 80 SKU (LucVT) · so sánh ≥3 LLM (ChienPX).
- **S1** (làm gối lên tuần S0, review 10/11): chat web hoàn chỉnh (TuTM) · LLM thật (ChienPX) · PolicyEngine v0 + 5 ca bench web (HungDQ) · CRUD sản phẩm + import 80 SKU + màn admin (LucVT). T-06.7 đã xong. Còn T-06.8 làm tiếp trong S2.
- **S2** (10–16/11, kéo lên 1 tuần; S3–S12 cũng kéo lên, decisions #24): simulator Shopee theo schema thật + RAG (ChienPX) · simulator TikTok Shop + lưu hội thoại (TuTM) · web store + dữ liệu thật kèm ảnh (LucVT) · bench 50 ca, tài liệu benchmark, bỏ FakeProvider, chuyển hướng (HungDQ). Vượt capacity có chủ đích: ChienPX 10 md, TuTM 7,5, LucVT 6,5, HungDQ 5.
