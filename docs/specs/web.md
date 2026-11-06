# Web (React + Vite)

> Cập nhật: 2026-11-06 · Task [T-06.1]
> Nguồn: `web/src/**`, `web/vite.config.ts`, `web/package.json`

React 19, Vite 8, TypeScript 7, react-router 7, lint bằng oxlint. Chưa có thư viện UI, state hay test. Chạy `npm run dev` (:5173) hoặc `run-web.bat`. Kiểm tra bằng `npm run build` (gồm `tsc -b`).

## Cấu trúc (modular theo feature)

```
src/main.tsx              mount App
src/app/App.tsx           router
src/app/ShopLayout.tsx    layout chung (banner demo, chat widget)
src/shared/types.ts       ChannelType, BotReply, PolicyViolation, Page<T>: khớp backend
src/shared/lib/http.ts    getJson, postJson, wsUrl (dùng location.host nên đi qua proxy Vite)
src/shared/lib/format.ts  định dạng (giá...)
src/shared/ui/            DemoBanner, global.css
src/features/<feature>/   index.ts (export public), *Page.tsx, api.ts, types.ts
```
Feature chỉ import từ `shared/` hoặc qua `index.ts` của feature khác, **không** import sâu vào file bên trong feature khác.

## Route

| Path | Component | Feature |
|---|---|---|
| `/` | `StorePage`: danh sách sản phẩm, lọc theo loại da | `features/store` (LucVT) |
| `/admin/policy` | `PolicyPlaygroundPage`: gửi 1 câu vào WEB/TELEGRAM/SHOPEE/TIKTOK qua simulator, so sánh rawText và finalText | `features/policy-playground` (HungDQ) |
| mọi trang trừ `/admin/*` | `ChatWidget` + `useChatSocket` (WS `/ws/chat`, trạng thái `connecting/open/closed`), gắn trong `ShopLayout` | `features/chat` (TuTM) |

## Proxy dev
`vite.config.ts`: `/api` chuyển tới `http://localhost:8080`, `/ws` chuyển tới `ws://localhost:8080` (bật ws). Backend cho phép origin `http://localhost:5173` (`WEB_ALLOWED_ORIGINS`).

## Đã biết
- `useChatSocket` đã có auto-reconnect sau 3s và nút thử lại, ChatWidget có banner báo mất kết nối rõ ràng, typing indicator và auto-scroll (xong T-06.1).
- Chưa có trang admin sản phẩm (T-08.3), chi tiết sản phẩm (US-09), đăng nhập (US-13), inbox (US-14).

## Thêm feature
1. Tạo `src/features/<ten>/` gồm `index.ts`, `XxxPage.tsx`, `api.ts`.
2. Thêm route vào `App.tsx`.
3. Kiểu dùng chung thì để trong `shared/types.ts`, phải khớp [api-contracts.md](api-contracts.md).
4. Cập nhật bảng Route ở trên.
