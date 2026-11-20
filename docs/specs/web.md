# Web (React + Vite)

> Cập nhật: 2026-11-20 · T-33.2 màn `/admin/promotions` (`features/promotion-admin`), nút Lịch sử giá ở `/admin/products`, `formatVnDateTime`/`toDateTimeLocal`, `http.ts` nhận `DELETE`; 2026-11-19 · T-43.3 chat widget không tải catalog: thẻ gợi ý lấy từ `BotReply.products`, tên SP có link `/products/:id`; 2026-11-18 · T-22.2, T-22.4 trang `/account/link` (lấy mã OMNI, xác nhận liên kết + consent), menu "Liên kết sàn", `http.ts` nhận `204`; T-49.3 cột "Đồng bộ sàn" trên `/admin/products` (nhãn theo kênh, chi tiết, thử lại, tự làm mới); T-43.1 `ProductCard` cho danh sách store và chat widget (`shared/types.ts`); `DemoOrderBox` cần đăng nhập, gửi JWT (decisions #40); T-13.2 `/login`, `features/account` (`account.css`), `AccountMenu` trên header, chat gửi token; T-14.1 màn inbox `/admin/conversations`; US-56 (T-56.1, T-56.2) giao diện theo design system Soft UI Evolution, tokens.css, @fontsource, chat mặc định thu nhỏ (decisions #38); 2026-11-13 · T-09.3 nút Đặt thử trên trang chi tiết; T-09.1 trang chi tiết `/products/:id`, lọc danh mục/giá trên store, `shared/vocabulary.ts`; trước đó 2026-11-13 · T-04.1 giá theo kênh `prices` (store dùng `prices.WEB`); trước đó T-09.1 store phân trang, bỏ link nguồn từng thẻ
> Nguồn: `web/src/**`, `web/vite.config.ts`, `web/package.json`

React 19, Vite 8, TypeScript 7, react-router 7, lint bằng oxlint. Chưa có thư viện UI, state hay test. Chạy `npm run dev` (:5173) hoặc `run-web.bat`. Kiểm tra bằng `npm run build` (gồm `tsc -b`).

## Cấu trúc (modular theo feature)

```
src/main.tsx              mount App (nạp font @fontsource)
src/app/App.tsx           router
src/app/ShopLayout.tsx    layout chung (skip link, header tên shop Lumi, banner demo, chat widget, footer)
src/shared/types.ts       ChannelType, BotReply, PolicyViolation, Page<T>, Product, ProblemDetail: khớp backend
src/shared/lib/http.ts    requestJson (GET/POST/PUT/PATCH/DELETE, 204 thì trả undefined), getJson, postJson, ApiError (status + ProblemDetail), wsUrl (dùng location.host nên đi qua proxy Vite)
src/shared/lib/format.ts  formatVnd; formatVnDateTime, toDateTimeLocal (luôn theo giờ Asia/Ho_Chi_Minh, khuyến mãi và lịch sử giá)
src/shared/ui/            tokens.css, global.css (.btn, .chip, .badge, .table-wrap, .skeleton dùng chung), icons.tsx, DemoBanner
src/shared/vocabulary.ts  danh mục, loại da, vấn đề da, buổi dùng + nhãn tiếng Việt (store và admin dùng chung, khớp CatalogVocabulary.java)
src/features/<feature>/   index.ts (export public), *Page.tsx, api.ts, types.ts, CSS riêng (<feature>.css)
```
Feature chỉ import từ `shared/` hoặc qua `index.ts` của feature khác, **không** import sâu vào file bên trong feature khác.

## Route

| Path | Component | Feature |
|---|---|---|
| `/` | `StorePage`: danh sách sản phẩm phân trang 20 SKU/trang (`sort=id`), mỗi phần tử là `ProductCard` (thẻ gọn, không có INCI/mô tả; T-43.1). Lọc **5 loại da** (chip), **danh mục** và **khoảng giá web** (select), gọi `GET /api/products?skinType&category&minPrice&maxPrice` (T-09.2). Bộ lọc và trang nằm trên URL (`?skin=&cat=&price=min-max&page=`) nên từ chi tiết bấm Quay lại vẫn giữ chỗ; đổi bộ lọc thì về trang 1, có nút Bỏ lọc. Thẻ sản phẩm (ảnh + tên) link tới `/products/:id`. Cuối trang: dòng ghi nguồn Open Beauty Facts (`Attribution`, **bắt buộc**) | `features/store` (LucVT) |
| `/products/:id` | `ProductDetailPage` (T-09.1): ảnh, hãng, tên, giá web (`prices.WEB`), tồn kho, mô tả, loại sản phẩm, loại da, vấn đề da, buổi dùng, dung tích, PAO, SKU, **INCI đầy đủ**, link nguồn OBF + ghi chú giấy phép ảnh. **Đặt thử** (`DemoOrderBox`, T-09.3): chưa đăng nhập thì chỉ có link "Đăng nhập để đặt thử" (`/login?next=` trang hiện tại, decisions #40); đã đăng nhập thì chọn số lượng 1..min(10, tồn), `POST /api/orders` kèm `Authorization: Bearer`, `401` thì đăng xuất và báo hết phiên; hiện hộp "Đã tạo đơn thử nghiệm #id", tổng tiền, trạng thái DEMO, email tài khoản, câu không thanh toán/không giao; lỗi `errors[]` hiện dưới nút; hết hàng thì không có nút. API `404` (sản phẩm ẩn/không có, G-06) hoặc `400` (id sai) thì báo "không tồn tại hoặc đã ngừng bán". Nút Quay lại: lùi lịch sử, vào thẳng bằng link thì về `/` | `features/store` (LucVT) |
| `/login` | `LoginPage` (T-13.2): bước 1 email + ô đồng ý xử lý dữ liệu cá nhân (bắt buộc) → `POST /api/auth/otp`; bước 2 ô mã 6 số, đếm ngược "Gửi lại mã sau Ns" theo `resendAfterSeconds`, nút Đổi email → `POST /api/auth/otp/verify`. Câu lỗi theo `code`: sai mã (còn N lần), hết hạn, khoá (N phút), gửi quá nhiều (N giây/phút). Gửi mã gặp `429` ≤ 60 s (vd. tải lại trang) thì vẫn chuyển sang bước nhập mã. Đúng thì về `?next=` (chỉ đường bắt đầu bằng `/`, không `//`) hoặc `/`. Dev hiện dòng "mã in trong log backend" | `features/account` (LucVT) |
| `/account/link` | `LinkAccountPage` (T-22.2, T-22.4, US-22): chưa đăng nhập thì nút "Đăng nhập để liên kết" (`/login?next=/account/link`). (1) **Lấy mã** `POST /api/me/link-codes`: mã `OMNI-XXXXXX` chữ lớn, nút Sao chép, đếm ngược `m:ss` tới `expiresAt`, hướng dẫn 3 bước, "Lấy mã khác" (mã cũ mất hiệu lực); hết giờ thì báo hết hạn + "Lấy mã mới". (2) **Xác nhận** `GET /api/me/link-requests`: mỗi yêu cầu 1 thẻ "Liên kết tài khoản Shopee ***abc?" kèm hạn còn lại, ô đồng ý (bắt buộc, nút Xác nhận mờ khi chưa tick) → `POST .../confirm`; nút "Không phải tôi" → `POST .../reject`. Lỗi `410`/`409` có câu riêng, `401` thì đăng xuất | `features/account` (LucVT) |
| `/admin/products` | `ProductAdminPage`: đăng nhập admin, bảng sản phẩm (gồm SKU ẩn, tìm, lọc trạng thái, phân trang), dialog thêm/sửa, nút ẩn/hiện. Cột giá và ô giá sinh theo `GET /api/admin/sales-channels` (không viết cứng 3 kênh, V13) | `features/product-admin` (LucVT) |
| `/admin/promotions` | `PromotionAdminPage` (T-33.2, US-33): đăng nhập admin (dùng lại `LoginForm`), bảng khuyến mãi (kênh, tên + mã voucher hoặc số SKU, mức giảm, thời gian giờ VN, nhãn Đang chạy / Sắp diễn ra / Hết hạn / Đã tắt), lọc chip kênh + trạng thái trên URL (`?channel=&status=&page=`), phân trang 20; dialog thêm/sửa; Tắt/Bật; Xoá có hỏi xác nhận. TC: `docs/testcases/US-33.md` | `features/promotion-admin` (LucVT) |
| `/admin/conversations` | `ConversationInboxPage` (T-14.1): đăng nhập admin (dùng lại `LoginForm` của product-admin), bên trái danh sách hội thoại (kênh, thời gian, người dùng, tin cuối, badge "N câu bị can thiệp" / "N cần nhân viên", phân trang 20), bên phải luồng tin của hội thoại đang chọn. Câu bot hiện "Khách nhận (finalText)" và "Câu gốc của AI (rawText)" khi khác nhau, danh sách luật đã can thiệp; **bị chặn tô đỏ nhạt, bị sửa tô vàng nhạt**, badge handoff và trạng thái gửi (`FAILED`, `RETRYING`). Lọc: chip kênh, Từ ngày / Đến ngày, "Có vi phạm", "Cần nhân viên"; bộ lọc và hội thoại đang mở nằm trên URL (`?channel=&from=&to=&violation=1&handoff=1&page=&id=`). ≤ 860 px thành 1 cột, có nút "‹ Danh sách". TC: `docs/testcases/US-14.md` | `features/conversation-inbox` (TuTM) |
| `/admin/policy` | `PolicyPlaygroundPage`: gửi 1 câu vào WEB/SHOPEE/TIKTOK qua simulator, so sánh rawText và finalText | `features/policy-playground` (HungDQ) |
| mọi trang trừ `/admin/*` | `ChatWidget` + `useChatSocket` (WS `/ws/chat`, trạng thái `connecting/open/closed`), gắn trong `ShopLayout`. Mặc định thu nhỏ thành nút nổi góc phải dưới (tránh che giá/đặt hàng), lưu trạng thái trong `sessionStorage`, Esc để thu nhỏ, trên điện thoại thành bottom sheet. Đã đăng nhập thì mở `/ws/chat?token=<JWT>`. `ShopLayout` đặt `key` = customer id (hoặc `guest`) nên đăng nhập/đăng xuất thì widget dựng lại: xoá tin của người trước, nối lại với token mới | `features/chat` (TuTM, UI HungDQ) |
| header (nav "Cửa hàng") mọi trang trừ `/admin/*` | `AccountMenu`: link "Đăng nhập" (kèm `?next=` trang hiện tại), hoặc link "Liên kết sàn" (`/account/link`) + email + nút Đăng xuất (xuống dòng ở màn hẹp). Khi mount gọi `refreshSession()` (`GET /api/me`, `401` thì đăng xuất) | `features/account` (LucVT) |

## Proxy dev
`vite.config.ts`: `/api` chuyển tới `http://localhost:8080`, `/ws` chuyển tới `ws://localhost:8080` (bật ws). Backend cho phép origin `http://localhost:5173` (`WEB_ALLOWED_ORIGINS`).

## Đã biết
- Design system: xem chi tiết tại [design-system.md](design-system.md). Mọi màu sắc, khoảng cách, font đi qua `tokens.css`.
- Giỏ hàng nhiều sản phẩm chưa có (API `/api/orders` đã nhận nhiều dòng).

## Tài khoản người mua (`features/account`, T-13.2)
- `session.ts`: phiên `{token, expiresAt, customer}` trong `localStorage` (`omniai.customer.session`, bọc try/catch; bị chặn thì chỉ giữ trong bộ nhớ tab). Store nhỏ + `useCustomerSession()` (`useSyncExternalStore`), đồng bộ giữa các tab qua sự kiện `storage`. Token hết hạn thì coi như chưa đăng nhập.
- Export qua `index.ts` (import `account.css`: ô đồng ý, ô OTP, menu email; khung `.login-form` dùng chung với `product-admin/admin.css`): `LoginPage`, `LinkAccountPage`, `AccountMenu`, `useCustomerSession`. `LoginPage` và `LinkAccountPage` tự render `<main id="main">`. `features/chat` dùng `useCustomerSession` để lấy token.
- Trang liên kết (`LinkAccountPage.tsx`, T-22.2, T-22.4): backend chỉ lưu hash của mã nên mã đang hiện được giữ trong `sessionStorage` (`omniai.link.code` = `{customerId, code, expiresAt}`, bọc try/catch); F5 vẫn còn, đổi tài khoản hoặc hết hạn thì bỏ. Hỏi lại `GET /api/me/link-requests` mỗi 5 s khi còn mã hoặc còn yêu cầu chờ và tab đang hiện, và khi cửa sổ được focus lại (chưa có WebSocket cho sự kiện này). Xác nhận xong thì xoá mã và hiện "Đã liên kết tài khoản …". Bước gõ mã trong chat sàn là T-22.3; trước khi có thì test bằng API nội bộ (`docs/testcases/US-22.md` mục B).
- Test tay: `docs/testcases/US-13.md`, `docs/testcases/US-22.md`.

## Admin sản phẩm (`features/product-admin`, T-08.3)
- Đăng nhập: form gửi thử `GET /api/admin/products?size=1` với header `Authorization: Basic …`. Đúng thì lưu token vào `sessionStorage` (`omniai.admin.basic`, đóng tab là mất). Gặp `401` ở bất kỳ request nào thì xoá token, quay về form.
- Mọi request admin gửi kèm `X-Requested-With: XMLHttpRequest` để backend trả `401` không có `WWW-Authenticate`, nhờ vậy trình duyệt không bật hộp đăng nhập riêng.
- UI **không** tự kiểm luật dữ liệu (tránh có bản thứ 3 của luật). Gửi thẳng lên backend, lỗi `errors[]` của ProblemDetail hiện dưới từng ô, `409` gắn vào ô SKU. `vocabulary.ts` chỉ là danh sách lựa chọn + nhãn tiếng Việt, phải khớp `CatalogVocabulary.java`.
- Sửa thì không gửi `active` (backend giữ nguyên). Ẩn/hiện dùng `PATCH /{id}/active`. Không có nút xoá (G-07). SKU khoá khi sửa.
- **Đồng bộ sàn (T-49.3, US-49):** có kênh trong `GET /api/admin/sync/channels` thì bảng thêm cột "Đồng bộ sàn": mỗi kênh 1 nhãn (`SyncStatusCell.tsx`) gộp 3 phần nội dung/giá/tồn: có phần FAILED → **Lỗi**, còn PENDING → **Đang chờ**, đủ SYNCED → **Đã đồng bộ**, chưa có dòng → **Chưa đồng bộ**. Trạng thái lấy bằng `GET /api/admin/sync/products/by-ids` cho đúng id của trang đang xem; còn phần PENDING thì hỏi lại mỗi 3 s, hết thì dừng; thêm/sửa/ẩn sản phẩm cũng tải lại. Bấm nhãn mở 1 hàng chi tiết rộng hết bảng (`SyncDetail`): từng phần, thời điểm lên sàn, giá trị "Trên sàn" (SYNCED) / "Sắp gửi" (PENDING) / "Chưa gửi được" (FAILED) lấy từ sản phẩm trên hub (backend không lưu giá trị đã gửi; SYNCED nghĩa là sàn giữ đúng giá trị hiện tại của hub), lỗi gần nhất, mã trên sàn; phần lỗi có nút **Thử lại** (`POST …/retry?aspect=`). Lỗi tải trạng thái chỉ hiện 1 dòng nhỏ, không chặn bảng.
- **Lịch sử giá (T-33.2, US-33):** nút "Lịch sử giá" mỗi dòng mở `PriceHistoryDialog`: chip lọc kênh, bảng thời điểm (giờ VN), kênh, giá cũ (`null` = "Giá ban đầu"), giá mới, chênh (tăng đỏ, giảm xanh), 50 dòng/trang (`GET /api/admin/products/{id}/price-history`). Chỉ xem.
- Test tay: `docs/testcases/US-08.md` mục D; đồng bộ sàn: `docs/testcases/US-49.md`; lịch sử giá: `docs/testcases/US-33.md` mục D.
- Thẻ gợi ý trong chat (T-43.3): widget **không gọi `/api/products`**. `useChatSocket` giữ `products` của từng câu bot (`ChatLine.products`), `splitSuggestion(line.text, line.products)` tách dòng "N. Hãng — Tên — giáđ" thành thẻ (ảnh, SKU, giá Shopee/TikTok, nút Mua ngay), tên sản phẩm là link `/products/:id`. Câu không có `products` (kênh cũ, tra lỗi) thì dòng thẻ vẫn hiện tên + giá, khung ảnh vẽ theo nhóm hàng.
- `useChatSocket` đã có auto-reconnect sau 3s và nút thử lại, ChatWidget có banner báo mất kết nối rõ ràng, typing indicator và auto-scroll (xong T-06.1).
- `useChatSocket` có timeout 12 s sau mỗi lần gửi (T-06.8): hết giờ thì `isPending=false` và thêm dòng bot "Shop đang bận, bạn thử lại sau nhé."; nhận frame thì xóa timer. Frame `{error}` (`WsErrorReply`) hiện thành dòng lỗi (`ChatLine.error`, class `chat-line-error`), không coi là câu trả lời của bot.
- `LoginForm`, `loadToken`, `saveToken` export qua `features/product-admin/index.ts` để các màn `/admin/*` khác dùng chung. Khách đăng nhập rồi chưa có trang tài khoản / lịch sử đơn (US-23).

## Admin khuyến mãi (`features/promotion-admin`, T-33.2)
- Đăng nhập, `X-Requested-With`, `401` → về form: như admin sản phẩm. Kênh lấy từ `GET /api/admin/sales-channels`, không viết cứng.
- Form (`PromotionForm.tsx`): loại Voucher / Flash sale (khoá khi sửa), kênh, tên, Bắt đầu / Kết thúc bằng `datetime-local` **gửi nguyên chuỗi không offset** (backend hiểu là giờ VN); khi sửa đổi `+07:00` về giá trị ô bằng `toDateTimeLocal`, không phụ thuộc múi giờ máy. Voucher: mã (tự viết hoa), số tiền giảm, đơn tối thiểu. Flash sale: các dòng SKU + giá sale; rời ô SKU thì tra `GET /api/products/sku/{sku}` để hiện tên, giá niêm yết ở kênh đang chọn và % giảm (chỉ gợi ý).
- UI không tự kiểm luật. Lỗi `items[i].sku|salePrice` gắn về đúng dòng (bỏ dòng trống hẳn trước khi gửi, giữ bảng vị trí); `409` gắn vào ô mã voucher. Bật lại flash sale bị chồng giờ thì hiện câu lỗi của backend.

## Thêm feature
1. Tạo `src/features/<ten>/` gồm `index.ts`, `XxxPage.tsx`, `api.ts`.
2. Thêm route vào `App.tsx`.
3. Kiểu dùng chung thì để trong `shared/types.ts`, phải khớp [api-contracts.md](api-contracts.md).
4. Cập nhật bảng Route ở trên.
