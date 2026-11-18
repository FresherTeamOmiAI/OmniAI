# Design system web

> Cập nhật: 2026-11-18 · US-56 (T-56.1, T-56.2) · Owner: HungDQ
> Nguồn: `web/src/shared/ui/tokens.css`, `web/src/shared/ui/global.css`, `web/src/features/*/*.css`, `tools/web/check_contrast.py`

## Trách nhiệm
Một bộ token và quy tắc chung cho mọi trang web (store, chi tiết, chat widget, admin, thử luật kênh). Component **chỉ dùng token**, không viết mã màu trực tiếp. Đổi token thì chạy `python tools/web/check_contrast.py web/src/shared/ui/tokens.css` (phải 22/22 đạt).

## Nguồn quyết định
| Phần | Lấy từ | Chỉnh lại |
|---|---|---|
| Phong cách | [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) (commit e59ff37), `--design-system "beauty skincare cosmetics shop"`: **Soft UI Evolution**, bóng mềm nhiều lớp, bo 8–16 px, chuyển động 200–300 ms. Tránh: màu neon, chuyển động gắt, dark mode | Truy vấn "e-commerce" chung ra xanh lá/cam, không hợp shop mỹ phẩm nên không dùng |
| Màu | Bảng "Beauty/Spa/Wellness": hồng `#EC4899`, tím nhạt `#8B5CF6`, nền `#FDF2F8`, chữ `#831843`, viền `#FBCFE8` | Hồng `#EC4899` chỉ đạt 3,5:1 trên nền trắng nên nút/link dùng hồng đất `#A3245A` (7,1:1, màu cũ của shop). Tím dùng bản đậm `#6D28D9` cho chữ. Viền ô nhập đậm hơn cho đủ 3:1 |
| Chữ | Tiêu đề: Playfair Display (hồ sơ Beauty). Chữ chính: **Be Vietnam Pro** (cặp "Vietnamese Friendly" của skill) | Inter của hồ sơ Beauty đổi sang Be Vietnam Pro vì vẽ dấu tiếng Việt tốt hơn. Cả 2 font có bộ `vietnamese`, tự host bằng `@fontsource` (5.3.0, cố định phiên bản), demo không cần mạng tới Google Fonts |
| Chuyển động | [emilkowalski/skills](https://github.com/emilkowalski/skills) `emil-design-eng` | Chỉ lấy quy tắc, không thêm thư viện animation |

## Token (`tokens.css`)
| Nhóm | Token | Dùng cho |
|---|---|---|
| Nền | `--color-bg` `#FDF2F8`, `--color-surface` trắng, `--color-surface-2`, `--color-border` (trang trí), `--color-border-strong` (ô nhập, chip, nút viền: ≥ 3:1) | Trang, thẻ, hàng bảng, viền |
| Chữ | `--color-text` (chính), `--color-heading` `#831843`, `--color-muted` (phụ, ≥ 6:1) | |
| Hành động | `--color-primary` `#A3245A`, `-hover`, `-soft` (chip/tab đang chọn), `--color-on-primary`, `--color-focus` | Nút chính, link, giá, focus |
| AI | `--color-accent` `#6D28D9`, `--color-accent-soft` | Bong bóng bot, avatar trợ lý |
| Trạng thái | `--color-success*` (xanh), `--color-warning*` (vàng, banner thử nghiệm), `--color-danger*` (đỏ, chỉ 1 màu đỏ) | Luôn kèm chữ, không chỉ dùng màu |
| Khác | `--color-dot-*` (chấm trạng thái chat), `--tone-0..5` (hộp minh hoạ khi thiếu ảnh), `--color-backdrop` | |
| Chữ | `--font-sans`, `--font-serif`, `--font-mono`; cỡ `--text-xs` 12 → `--text-3xl` 30 px; `--leading-body` 1,6 | Thân bài 16 px, ô nhập 16 px (iOS không phóng to) |
| Khoảng cách | `--space-1` 4 px … `--space-12` 48 px (bước 4 px); `--gutter` 24 px, 16 px trên màn ≤ 640 px; `--container` 1120 px | |
| Hình khối | `--radius-sm` 8 (ô nhập), `-md` 12, `-lg` 16 (thẻ, dialog, chat), `-pill` (nút, chip, badge); `--shadow-sm/md/lg` ngả hồng | |
| Kích thước | `--control-h` 44 px (nút, ô nhập), `--control-h-sm` 36 px | Mục tiêu bấm ≥ 24 px (WCAG 2.2) |
| Lớp | `--z-header` 10, `--z-chat` 40, `--z-skip` 100; dialog dùng top layer của `<dialog>` | |
| Chuyển động | `--ease-out` `cubic-bezier(0.23, 1, 0.32, 1)`, `--ease-in-out`; `--dur-fast` 160, `--dur` 200, `--dur-slow` 280 ms | |

## Quy tắc component
- **Nút** `.btn` (viền), `.btn-primary` (1 nút chính mỗi vùng), `.btn-ghost`, `.btn-sm`, `.btn-icon` (luôn có `aria-label`). Nhấn: `transform: scale(0.97)`.
- **Chip lọc** `.chip`: có `aria-pressed`; đang chọn thì nền hồng nhạt + chấm tròn + chữ đậm.
- **Badge** `.badge-on` (xanh), `.badge-off` (xám), `.badge-warn` (vàng). Tồn kho trên thẻ dùng badge.
- **Ô nhập**: label hiện (hoặc `.sr-only` khi đã có ngữ cảnh), lỗi đặt ngay dưới ô và nối bằng `aria-describedby`. Không tự kiểm luật dữ liệu ở UI (decisions #20).
- **Trạng thái**: mỗi trang có đang tải (khung `.skeleton`, `aria-busy`), rỗng (`.state` + hành động như "Bỏ lọc"), lỗi (`.state-error`, `role="alert"`, nút "Thử lại").
- **Thẻ sản phẩm**: ảnh 1:1 `object-fit: contain`, `alt=""` vì tên ở ngay dưới; hãng, tên 2 dòng, giá `tabular-nums`, badge tồn. INCI chỉ ở trang chi tiết. Ảnh lỗi thì hiện hình chai (`ProductImage`).
- **Bảng** `.table-wrap`: hover hàng, hàng cuối không viền đôi, số căn phải `tabular-nums`.
- **Dialog**: `<dialog>` + `showModal()`, vẫn ở giữa màn hình, đóng thì trả focus về nút đã mở.
- **Chat widget**: mặc định thu nhỏ thành nút ở góc phải dưới (không che giá và nút Đặt thử), trạng thái giữ trong `sessionStorage`. Panel mở từ góc của nút, Esc để thu nhỏ, vùng tin `role="log"`; trên điện thoại thành sheet dưới.

## Chuyển động (emil-design-eng)
- Chỉ animate `transform`, `opacity`, màu. Không `transition: all`. Không xuất hiện từ `scale(0)` (bắt đầu từ 0.98 + mờ).
- Vào/ra dùng ease-out, dưới 300 ms. Không dùng ease-in. Panel/dialog dùng `@starting-style` (trình duyệt cũ thì hiện ngay, không lỗi).
- Thứ lặp lại nhiều (gõ phím, chuyển tab) không animate. Hover thẻ chỉ nhấc 2 px.
- `prefers-reduced-motion: reduce`: tắt animation, transition, cuộn mượt (`global.css`).

## Checklist trước khi merge UI
- [ ] Không có mã màu ngoài `tokens.css`: `grep -rnE "#[0-9a-fA-F]{3,8}\b|rgba?\(" web/src --include=*.css --include=*.tsx | grep -v tokens.css` rỗng.
- [ ] `npm run lint`, `npm run build` pass; script tương phản 22/22.
- [ ] Bàn phím: Tab đi hết trang, focus thấy rõ, link "Bỏ qua, tới nội dung chính" ở đầu trang.
- [ ] 390 px: không tràn ngang, danh sách 1 cột (TC-09-50).
- [ ] Câu chữ mà TC kiểm giữ nguyên (US-06, US-08 mục D, US-09 mục C, D).
- [ ] Icon là SVG (`shared/ui/icons.tsx`), không dùng emoji làm icon.
