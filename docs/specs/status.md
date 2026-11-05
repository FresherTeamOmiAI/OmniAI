# Trạng thái hiện tại

> Cập nhật: 2026-11-05 · commit a79fc37 · Sprint hiện tại: **S0 (03–09/11/2026)**

Bảng này cho biết cái gì đã chạy được, cái gì mới là khung, cái gì chưa có. Agent đọc file này **trước khi** đi tìm code để khỏi tìm thứ chưa tồn tại. Kế hoạch chi tiết: `tools/tracking/backlog.py` và GitHub Issues (milestone Sprint 0–12).

Chú thích: **Done** = chạy được, có test · **Khung** = có code tối thiểu/stub, cần làm tiếp · **Chưa** = chưa có code.

## Theo module

| Phần | Trạng thái | Ghi chú | US |
|---|---|---|---|
| Docker (Postgres+pgvector, Redis, RabbitMQ) | Done | Redis và RabbitMQ chưa được dùng | |
| Script chạy Windows `tools/systembat` | Done | setup / run-all / stop-all / test-all / bench | US-02 |
| CanonicalMessage + ChannelAdapter | Done | | |
| MessageRouter + idempotency | Khung | Idempotency in-memory, chưa lưu hội thoại | US-06, US-11, US-29 |
| Kênh WEB (WebSocket) | Khung | channelUserId = session id, widget chưa reconnect | US-06 |
| Simulator API | Done | Dùng cho bench và `/admin/policy` | US-07 |
| Kênh Telegram / Shopee / TikTok / Messenger | Chưa | Đã có YAML luật | US-17, 21, 25 |
| PolicyEngine + YAML | Done (v0) | YAML sàn là bản nháp | US-07, US-20 |
| AI Brain `/chat` + LLMProvider | Khung | Chỉ có `fake` | US-06 |
| Luật thành phần | Khung | Bản mẫu, cần chuyên gia review | US-27 |
| RAG / tool / agent loop | Chưa | | US-10, US-15 |
| Catalog: GET list/detail | Done | 8 SKU mẫu | |
| Catalog: schema đợt 1 (V4) | Done | Thêm mô tả, vấn đề da, buổi dùng; NOT NULL + CHECK. Chưa có cột embedding (US-10) | US-04 |
| Catalog: file 80 SKU (`shared/catalog/products.csv`) | Done | Có validator `tools/catalog/validate_catalog.py`. Chưa import vào DB (T-08.2) | US-04 |
| Catalog: CRUD admin, import CSV | Chưa | | US-08 |
| Customer / ChannelIdentity entity | Khung | Chưa có API | |
| Identity (OTP, deep-link, OMNI-code) | Chưa | Mới có package-info | US-13, 18, 22 |
| Order, Promotion | Chưa | Mới có package-info | US-09, 33 |
| Web store (list + lọc loại da) | Khung | Chưa có trang chi tiết, chưa đặt hàng ảo | US-09 |
| Trang thử luật kênh `/admin/policy` | Done | | UC-24 |
| Bench runner + policy_check | Done | 10 ca mẫu | US-12 |
| CI | Chưa | | US-16 |
| docs/specs + rule cho agent | Done | Stop hook `tools/agent/specs_guard.py` | |

## Lỗ hổng / nợ kỹ thuật đã biết
- Idempotency chỉ nằm trong bộ nhớ, sẽ mất khi restart và sai khi chạy nhiều instance (S7 chuyển Redis).
- `GET /api/products/{id}` vẫn trả sản phẩm `active=false`.
- Backend/ai-brain không tự đọc `.env` (script `.bat` nạp hộ, IDE thì phải tự cấu hình).
- Chưa có `mvnw`, mỗi máy phải tự cài Maven.

## Sprint S0–S1 (tóm tắt)
- **S0** (03–09/11): SRS, US, UC (HungDQ + cả team review) · setup môi trường (cả 4) · spike chat API Shopee/TikTok (TuTM) · schema + 80 SKU (LucVT) · so sánh ≥3 LLM (ChienPX).
- **S1** (10–16/11): chat web hoàn chỉnh (TuTM) · LLM thật (ChienPX) · PolicyEngine v0 + 5 ca bench web (HungDQ) · CRUD sản phẩm + import 80 SKU + màn admin (LucVT).
