# Hợp đồng API

> Cập nhật: 2026-11-03 · commit c88bfc6
> Nguồn: `backend/.../channel/simulator/*`, `channel/web/*`, `catalog/ProductController.java`, `brain/*`, `common/*`, `ai-brain/app/main.py`, `ai-brain/app/schemas.py`, `shared/schemas/canonical-message.schema.json`, `web/src/shared/types.ts`

JSON phía backend dùng **camelCase**. Riêng hợp đồng backend → ai-brain dùng **snake_case**.

## Backend (cổng 8080)

| Method | Path | Request | Response | Ghi chú |
|---|---|---|---|---|
| POST | `/api/simulator/{channel}/messages` | `{messageId?, userId*, text*}` | `BotReply` | `channel` viết thường: web, telegram, messenger, shopee, tiktok. Bỏ trống `messageId` thì server tự sinh UUID; gửi trùng `messageId` thì nhận lại kết quả cũ. Dùng cho bench và trang `/admin/policy`. |
| GET | `/api/products?skinType=&page=&size=` | | `PageResponse<Product>` = `{items, page, size, total}` | Chỉ trả sản phẩm `active=true`. `skinType` lọc kiểu "contains, không phân biệt hoa thường". Mặc định size 20. |
| GET | `/api/products/{id}` | | `Product` hoặc 404 | Hiện **không** lọc theo active. |
| WS | `/ws/chat` | text frame `{messageId, text}` | text frame `BotReply` | Kênh WEB. Origin cho phép: `WEB_ALLOWED_ORIGINS`. Text rỗng thì bỏ qua. |
| GET | `/actuator/health`, `/actuator/info` | | | |

Chưa có: CRUD admin sản phẩm (US-08, S1), `/api/conversations` (US-11, S2), auth (US-13, S3).

## Kiểu dữ liệu chung (`com.omniai.common`, `web/src/shared/types.ts`)

- `ChannelType`: `WEB, TELEGRAM, MESSENGER` (owned = kênh shop sở hữu, được gửi link/so sánh kênh) | `SHOPEE, TIKTOK` (sàn).
- `CanonicalMessage(messageId, channel, channelUserId, customerId?, conversationId, text, receivedAt, metadata)`. JSON Schema trong `shared/schemas/canonical-message.schema.json`. Hiện `conversationId` = `<kênh thường>:<userId>`, `customerId` luôn null (chưa liên kết danh tính).
- `BotReply(messageId, channel, rawText, finalText, violations[], blocked, handoff)`:
  - `rawText`: câu AI nói. `finalText`: câu khách nhận sau policy.
  - `violations[]`: `PolicyViolation(ruleId, action: BLOCK|REDACT|REPLACE, matched)`.
  - `blocked`: cả câu bị thay bằng `fallback_message`. `handoff`: cần nhân viên.
- `Product`: `id, sku, name, brand, category, skinTypes (csv), ingredientsInci (csv), sizeMl, paoMonths, priceWeb, priceShopee, priceTiktok, stock, active`.

## AI Brain (cổng 8000)

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | | `{status, provider}` |
| POST | `/chat` | `ChatRequest` | `ChatResponse` |

- `ChatRequest` (Pydantic) = `BrainRequest` (Java record): `message_id, channel, channel_owned, user_id, customer_id?, text`.
- `ChatResponse` = `BrainResponse`: `reply, provider, warnings[] (mã cảnh báo, vd "conflict:aha+retinoid"), handoff`.
- Backend gọi qua `HttpBrainClient` với timeout 10s (`omniai.brain.timeout`). Nếu lỗi thì trả `BrainResponse.unavailable()`: câu chờ + `handoff=true`, provider `"none"`.
- Đổi field ở một phía thì **phải sửa cả 2 phía**: `schemas.py` và `BrainRequest`/`BrainResponse`.

## Quy tắc khi đổi hợp đồng
1. Sửa cả nơi gửi lẫn nơi nhận (Java ↔ Python ↔ `web/src/shared/types.ts`).
2. Nếu đổi `CanonicalMessage` thì sửa JSON Schema trong `shared/schemas/`.
3. Cập nhật bảng trong file này và [status.md](status.md).
