# Web (React + Vite)

> Cập nhật: 2026-11-18 · T-14.1 màn inbox `/admin/conversations`; US-56 (T-56.1, T-56.2) giao diện theo design system Soft UI Evolution, tokens.css, @fontsource, chat mặc định thu nhỏ (decisions #38); 2026-11-13 · T-09.3 nút Đặt thử trên trang chi tiết; T-09.1 trang chi tiết `/products/:id`, lọc danh mục/giá trên store, `shared/vocabulary.ts`; trước đó 2026-11-13 · T-04.1 giá theo kênh `prices` (store dùng `prices.WEB`); trước đó T-09.1 store phân trang, bỏ link nguồn từng thẻ
> Nguồn: `web/src/**`, `web/vite.config.ts`, `web/package.json`

React 19, Vite 8, TypeScript 7, react-router 7, lint bằng oxlint. Chưa có thư viện UI, state hay test. Chạy `npm run dev` (:5173) hoặc `run-web.bat`. Kiểm tra bằng `npm run build` (gồm `tsc -b`).

## Cấu trúc (modular theo feature)

```
src/main.tsx              mount App (nạp font @fontsource)
src/app/App.tsx           router
src/app/ShopLayout.tsx    layout chung (skip link, header tên shop Lumi, banner demo, chat widget, footer)
src/shared/types.ts       ChannelType, BotReply, PolicyViolation, Page<T>, Product, ProblemDetail: khớp backend
src/shared/lib/http.ts    requestJson, getJson, postJson, ApiError (status + ProblemDetail), wsUrl (dùng location.host nên đi qua proxy Vite)
src/shared/lib/format.ts  định dạng (giá...)
src/shared/ui/            tokens.css, global.css (.btn, .chip, .badge, .table-wrap, .skeleton dùng chung), icons.tsx, DemoBanner
src/shared/vocabulary.ts  danh mục, loại da, vấn đề da, buổi dùng + nhãn tiếng Việt (store và admin dùng chung, khớp CatalogVocabulary.java)
src/features/<feature>/   index.ts (export public), *Page.tsx, api.ts, types.ts, CSS riêng (<feature>.css)
```
Feature chỉ import từ `shared/` hoặc qua `index.ts` của feature khác, **không** import sâu vào file bên trong feature khác.

## Route

| Path | Component | Feature |
|---|---|---|
| `/` | `StorePage`: danh sách sản phẩm phân trang 20 SKU/trang (`sort=id`). Lọc **5 loại da** (chip), **danh mục** và **khoảng giá web** (select), gọi `GET /api/products?skinType&category&minPrice&maxPrice` (T-09.2). Bộ lọc và trang nằm trên URL (`?skin=&cat=&price=min-max&page=`) nên từ chi tiết bấm Quay lại vẫn giữ chỗ; đổi bộ lọc thì về trang 1, có nút Bỏ lọc. Thẻ sản phẩm (ảnh + tên) link tới `/products/:id`. Cuối trang: dòng ghi nguồn Open Beauty Facts (`Attribution`, **bắt buộc**) | `features/store` (LucVT) |
| `/products/:id` | `ProductDetailPage` (T-09.1): ảnh, hãng, tên, giá web (`prices.WEB`), tồn kho, mô tả, loại sản phẩm, loại da, vấn đề da, buổi dùng, dung tích, PAO, SKU, **INCI đầy đủ**, link nguồn OBF + ghi chú giấy phép ảnh. **Đặt thử** (`DemoOrderBox`, T-09.3): chọn số lượng 1..min(10, tồn), `POST /api/orders`, hiện hộp "Đã tạo đơn thử nghiệm #id", tổng tiền, trạng thái DEMO, câu không thanh toán/không giao; lỗi `errors[]` hiện dưới nút; hết hàng thì không có nút. API `404` (sản phẩm ẩn/không có, G-06) hoặc `400` (id sai) thì báo "không tồn tại hoặc đã ngừng bán". Nút Quay lại: lùi lịch sử, vào thẳng bằng link thì về `/` | `features/store` (LucVT) |
| `/admin/products` | `ProductAdminPage`: đăng nhập admin, bảng sản phẩm (gồm SKU ẩn, tìm, lọc trạng thái, phân trang), dialog thêm/sửa, nút ẩn/hiện. Cột giá và ô giá sinh theo `GET /api/admin/sales-channels` (không viết cứng 3 kênh, V13) | `features/product-admin` (LucVT) |
| `/admin/conversations` | `ConversationInboxPage` (T-14.1): đăng nhập admin (dùng lại `LoginForm` của product-admin), bên trái danh sách hội thoại (kênh, thời gian, người dùng, tin cuối, badge "N câu bị can thiệp" / "N cần nhân viên", phân trang 20), bên phải luồng tin của hội thoại đang chọn. Câu bot hiện "Khách nhận (finalText)" và "Câu gốc của AI (rawText)" khi khác nhau, danh sách luật đã can thiệp; **bị chặn tô đỏ nhạt, bị sửa tô vàng nhạt**, badge handoff và trạng thái gửi (`FAILED`, `RETRYING`). Lọc: chip kênh, Từ ngày / Đến ngày, "Có vi phạm", "Cần nhân viên"; bộ lọc và hội thoại đang mở nằm trên URL (`?channel=&from=&to=&violation=1&handoff=1&page=&id=`). ≤ 860 px thành 1 cột, có nút "‹ Danh sách". TC: `docs/testcases/US-14.md` | `features/conversation-inbox` (TuTM) |
| `/admin/policy` | `PolicyPlaygroundPage`: gửi 1 câu vào WEB/SHOPEE/TIKTOK qua simulator, so sánh rawText và finalText | `features/policy-playground` (HungDQ) |
| mọi trang trừ `/admin/*` | `ChatWidget` + `useChatSocket` (WS `/ws/chat`, trạng thái `connecting/open/closed`), gắn trong `ShopLayout`. Mặc định thu nhỏ thành nút nổi góc phải dưới (tránh che giá/đặt hàng), lưu trạng thái trong `sessionStorage`, Esc để thu nhỏ, trên điện thoại thành bottom sheet | `features/chat` (TuTM, UI HungDQ) |

## Proxy dev
`vite.config.ts`: `/api` chuyển tới `http://localhost:8080`, `/ws` chuyển tới `ws://localhost:8080` (bật ws). Backend cho phép origin `http://localhost:5173` (`WEB_ALLOWED_ORIGINS`).

## Đã biết
- Design system: xem chi tiết tại [design-system.md](design-system.md). Mọi màu sắc, khoảng cách, font đi qua `tokens.css`.
- Chưa có đăng nhập khách (US-13), giỏ hàng nhiều sản phẩm (API `/api/orders` đã nhận nhiều dòng).

## Admin sản phẩm (`features/product-admin`, T-08.3)
- Đăng nhập: form gửi thử `GET /api/admin/products?size=1` với header `Authorization: Basic …`. Đúng thì lưu token vào `sessionStorage` (`omniai.admin.basic`, đóng tab là mất). Gặp `401` ở bất kỳ request nào thì xoá token, quay về form.
- Mọi request admin gửi kèm `X-Requested-With: XMLHttpRequest` để backend trả `401` không có `WWW-Authenticate`, nhờ vậy trình duyệt không bật hộp đăng nhập riêng.
- UI **không** tự kiểm luật dữ liệu (tránh có bản thứ 3 của luật). Gửi thẳng lên backend, lỗi `errors[]` của ProblemDetail hiện dưới từng ô, `409` gắn vào ô SKU. `vocabulary.ts` chỉ là danh sách lựa chọn + nhãn tiếng Việt, phải khớp `CatalogVocabulary.java`.
- Sửa thì không gửi `active` (backend giữ nguyên). Ẩn/hiện dùng `PATCH /{id}/active`. Không có nút xoá (G-07). SKU khoá khi sửa.
- Test tay: `docs/testcases/US-08.md`, mục D.
- `useChatSocket` đã có auto-reconnect sau 3s và nút thử lại, ChatWidget có banner báo mất kết nối rõ ràng, typing indicator và auto-scroll (xong T-06.1).
- `useChatSocket` có timeout 12 s sau mỗi lần gửi (T-06.8): hết giờ thì `isPending=false` và thêm dòng bot "Shop đang bận, bạn thử lại sau nhé."; nhận frame thì xóa timer. Frame `{error}` (`WsErrorReply`) hiện thành dòng lỗi (`ChatLine.error`, class `chat-line-error`), không coi là câu trả lời của bot.
- Chưa có đăng nhập khách (US-13). `LoginForm`, `loadToken`, `saveToken` export qua `features/product-admin/index.ts` để các màn `/admin/*` khác dùng chung.

## Thêm feature
1. Tạo `src/features/<ten>/` gồm `index.ts`, `XxxPage.tsx`, `api.ts`.
2. Thêm route vào `App.tsx`.
3. Kiểu dùng chung thì để trong `shared/types.ts`, phải khớp [api-contracts.md](api-contracts.md).
4. Cập nhật bảng Route ở trên.
