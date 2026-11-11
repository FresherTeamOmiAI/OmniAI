# Web (React + Vite)

> Cập nhật: 2026-11-11 · commit c5ffbd9 · T-06.8
> Nguồn: `web/src/**`, `web/vite.config.ts`, `web/package.json`

React 19, Vite 8, TypeScript 7, react-router 7, lint bằng oxlint. Chưa có thư viện UI, state hay test. Chạy `npm run dev` (:5173) hoặc `run-web.bat`. Kiểm tra bằng `npm run build` (gồm `tsc -b`).

## Cấu trúc (modular theo feature)

```
src/main.tsx              mount App
src/app/App.tsx           router
src/app/ShopLayout.tsx    layout chung (banner demo, chat widget)
src/shared/types.ts       ChannelType, BotReply, PolicyViolation, Page<T>, Product, ProblemDetail: khớp backend
src/shared/lib/http.ts    requestJson, getJson, postJson, ApiError (status + ProblemDetail), wsUrl (dùng location.host nên đi qua proxy Vite)
src/shared/lib/format.ts  định dạng (giá...)
src/shared/ui/            DemoBanner, global.css (có .btn, .badge dùng chung)
src/features/<feature>/   index.ts (export public), *Page.tsx, api.ts, types.ts
```
Feature chỉ import từ `shared/` hoặc qua `index.ts` của feature khác, **không** import sâu vào file bên trong feature khác.

## Route

| Path | Component | Feature |
|---|---|---|
| `/` | `StorePage`: danh sách sản phẩm, lọc theo loại da | `features/store` (LucVT) |
| `/admin/products` | `ProductAdminPage`: đăng nhập admin, bảng sản phẩm (gồm SKU ẩn, tìm, lọc trạng thái, phân trang), dialog thêm/sửa, nút ẩn/hiện | `features/product-admin` (LucVT) |
| `/admin/policy` | `PolicyPlaygroundPage`: gửi 1 câu vào WEB/SHOPEE/TIKTOK qua simulator, so sánh rawText và finalText | `features/policy-playground` (HungDQ) |
| mọi trang trừ `/admin/*` | `ChatWidget` + `useChatSocket` (WS `/ws/chat`, trạng thái `connecting/open/closed`), gắn trong `ShopLayout` | `features/chat` (TuTM) |

## Proxy dev
`vite.config.ts`: `/api` chuyển tới `http://localhost:8080`, `/ws` chuyển tới `ws://localhost:8080` (bật ws). Backend cho phép origin `http://localhost:5173` (`WEB_ALLOWED_ORIGINS`).

## Đã biết
- Chưa có trang chi tiết sản phẩm (US-09), đăng nhập khách (US-13), inbox (US-14).

## Admin sản phẩm (`features/product-admin`, T-08.3)
- Đăng nhập: form gửi thử `GET /api/admin/products?size=1` với header `Authorization: Basic …`. Đúng thì lưu token vào `sessionStorage` (`omniai.admin.basic`, đóng tab là mất). Gặp `401` ở bất kỳ request nào thì xoá token, quay về form.
- Mọi request admin gửi kèm `X-Requested-With: XMLHttpRequest` để backend trả `401` không có `WWW-Authenticate`, nhờ vậy trình duyệt không bật hộp đăng nhập riêng.
- UI **không** tự kiểm luật dữ liệu (tránh có bản thứ 3 của luật). Gửi thẳng lên backend, lỗi `errors[]` của ProblemDetail hiện dưới từng ô, `409` gắn vào ô SKU. `vocabulary.ts` chỉ là danh sách lựa chọn + nhãn tiếng Việt, phải khớp `CatalogVocabulary.java`.
- Sửa thì không gửi `active` (backend giữ nguyên). Ẩn/hiện dùng `PATCH /{id}/active`. Không có nút xoá (G-07). SKU khoá khi sửa.
- Test tay: `docs/testcases/US-08.md`, mục D.
- `useChatSocket` đã có auto-reconnect sau 3s và nút thử lại, ChatWidget có banner báo mất kết nối rõ ràng, typing indicator và auto-scroll (xong T-06.1).
- `useChatSocket` có timeout 12 s sau mỗi lần gửi (T-06.8): hết giờ thì `isPending=false` và thêm dòng bot "Shop đang bận, bạn thử lại sau nhé."; nhận frame thì xóa timer. Frame `{error}` (`WsErrorReply`) hiện thành dòng lỗi (`ChatLine.error`, class `chat-line-error`), không coi là câu trả lời của bot.
- Chưa có trang admin sản phẩm (T-08.3), chi tiết sản phẩm (US-09), đăng nhập (US-13), inbox (US-14).

## Thêm feature
1. Tạo `src/features/<ten>/` gồm `index.ts`, `XxxPage.tsx`, `api.ts`.
2. Thêm route vào `App.tsx`.
3. Kiểu dùng chung thì để trong `shared/types.ts`, phải khớp [api-contracts.md](api-contracts.md).
4. Cập nhật bảng Route ở trên.
