# Đặc tả: bỏ tải hết sản phẩm, giảm query catalog (US-43)

> Viết 2026-11-18 sau review S2 · Cập nhật 2026-11-18 theo số đo thật ([perf-US-43.md](perf-US-43.md)): widget sau US-56, bcrypt của Basic, ai-brain trên Supabase · Người làm: **LucVT** (cả 3 phần, decisions #37) · Issue: US-43 #148, T-43.1 #149, T-43.2 #150, T-43.3 #264, T-43.4 #265
> Đọc kèm: [../specs/api-contracts.md](../specs/api-contracts.md), [../specs/data-model.md](../specs/data-model.md), [../specs/ai-brain.md](../specs/ai-brain.md)

## 1. Vì sao chậm (đã kiểm trong code ngày 18/11)
Backend **có** phân trang ở DB: `GET /api/products` → `ProductService.storeSearch` → `repository.findAll(Specification, Pageable)` (SQL `OFFSET/FETCH` + `count`). Store chỉ xin `size=20`. Cái chậm nằm ở chỗ khác:

| # | Chỗ | Hiện tại | Hậu quả |
|---|---|---|---|
| 1 | `web/src/features/chat/ChatWidget.tsx` (gắn ở `ShopLayout.tsx`) | Lúc review: mỗi lần mở trang store lặp `GET /api/products?page=N&size=100` tới hết catalog, chỉ để ghép ảnh/SKU vào thẻ gợi ý (`suggestionCards.tsx`). **Từ US-56: chỉ tải khi khách mở chat lần đầu** (`catalogWanted`) | Đo 18/11: mỗi lần mở chat lần đầu 4 request nối tiếp, ~543 KB JSON, 192 câu SQL (48/trang), ~290 ms local |
| 2 | `ai-brain/app/catalog_access.py:61-72` | Mỗi câu SQL mở **1 kết nối psycopg mới**, không pool, không cache | Mỗi câu chat 1–5 lần kết nối (SSL tới Supabase ~100–300 ms/lần) |
| 3 | `ai-brain/app/rag/search.py:151-166`, `agent/graph.py:48,58` | Câu hỏi có loại da/danh mục/giá thì đọc **cả view** `chatbot_catalog_search` (4 trang × 100 dòng, kèm vector 768 số dạng chữ), parse lại vector, tính cosine bằng Python | ~600 KB dữ liệu mỗi câu chat |
| 4 | `ai-brain/app/rules/sql_guard.py` (`CATALOG_SQL ... LIMIT 100`), `graph.py:45,68,71` | Prompt và danh sách giá được phép nói chỉ lấy **100/364 SKU đầu** (theo hãng A→C) | Bot không biết 264 SKU còn lại; giá đúng của SKU ngoài 100 dòng đầu bị `grounding.py` xoá. Prompt dài ~100 dòng làm LLM chậm thêm |
| 5 | `backend/.../catalog/ProductService.java:73-79,101` | Mỗi lần gọi store chạy 4 query bảng tra cứu (category, skin_type, skin_concern, sales_channel), kể cả khi không lọc | +4 câu SQL mỗi request |
| 6 | `backend/.../catalog/Product.java:71-74`, `Brand.java` | `brandRef` `@ManyToOne EAGER`, không batch | N+1: mỗi hãng khác nhau trên trang thêm 1 câu SQL (trung bình 6, tối đa 17 câu cho 20 SP) |
| 7 | `ProductController.java:37`, `AdminProductController.java:39`, `application.yml` | Không giới hạn `size` (Spring mặc định tối đa 2000), store không có sort mặc định, trả nguyên entity ~1,5 KB/SP (có INCI, mô tả, nguồn, barcode…), không gzip | Ai gọi `size=2000` là lấy hết catalog 1 lần |
| 8 | `backend/.../order/OrderService.java:51-54` | Mỗi dòng đơn gọi `findByIdAndActiveTrue` riêng | Đơn 20 dòng ~160 câu SQL |
| 9 | V13 cho phép giá NULL ở kênh tạm ngừng; `catalog_access.py:54`, `search.py:160-162`, `index_catalog.py:39-41` gọi `int(None)` | Lỗi tiềm ẩn | Có 1 giá NULL là `/chat` trả 500, mọi câu chat thành handoff |

Ước tính: 1 trang store ~17 câu SQL (12–28). Với Supabase pooler ở Sydney (~100 ms/vòng): ~2 s một trang store, ~13 s cho vòng tải của widget. Con số này là ước tính, phải đo lại ở T-43.2.

## 2. Mục tiêu đo được (tiêu chí nghiệm thu chung)
- Mở trang store **và mở chat**: widget không gọi `/api/products`. Mở store chỉ còn 1 request của store (đã đúng từ US-56), mở chat không thêm request nào.
- Một trang store 20 SP: **≤ 8 câu SQL** (đếm bằng Hibernate statistics trong test).
- `GET /api/products?size=500` không trả quá 100 SP. Sort trường lạ trả 400 ProblemDetail.
- JSON được gzip (header `Content-Encoding: gzip` khi request có `Accept-Encoding: gzip`).
- ai-brain: 10 câu chat liên tiếp trong thời gian TTL chỉ đọc DB **1 lần**; sau khởi động không mở kết nối mới cho mỗi câu.
- SKU ở vị trí > 100 (vd. `NIVE-SUN-02`) được gợi ý thì giá vẫn giữ nguyên trong câu trả lời.
- p95 API store/chi tiết/`/api/conversations` < 300 ms trên local (AC US-43), có số trước/sau.

## 3. Phần B: backend catalog (T-43.1, #149), làm đầu tiên
1. `application.yml`: `spring.jpa.properties.hibernate.default_batch_fetch_size: 50` → hết N+1 hãng (ghi chú trong `Product.java:50-51` đã hứa điều này).
2. Bảng tra cứu: nạp 1 lần rồi giữ trong bộ nhớ (chỉ đổi qua migration). Ví dụ `@Cacheable("vocabulary")` + `spring-boot-starter-cache` (ConcurrentMap mặc định), hoặc nạp lười vào 1 field `volatile`. `checkStoreFilter` chỉ kiểm tham số có mặt.
3. `spring.data.web.pageable.max-page-size: 100`. Sửa `StoreFilterApiTest.java:101,135` đang dùng `size=500` (lặp trang hoặc đếm bằng repository).
4. `@PageableDefault(size = 20, sort = "id")` cho list store và admin. Chỉ cho sort theo `id`, `name`, `createdAt`; trường khác trả 400 (bổ sung `AdminProductExceptionHandler` hoặc kiểm trong controller).
5. DTO thẻ cho `GET /api/products`: `id, sku, brand, name, category, skinTypes, imageUrl, prices, stock`. Bỏ INCI, mô tả, nguồn, giấy phép, barcode khỏi list. `GET /api/products/{id}` giữ nguyên. Admin list giữ nguyên (form sửa cần đủ trường). Thẻ store trên UI mới (PR US-56) không còn hiện INCI nên đổi được; merge sau PR US-56.
6. `server.compression.enabled: true`, `mime-types: application/json`, `min-response-size: 1KB`.
7. `OrderService.createDemo`: lấy mọi sản phẩm của đơn bằng 1 query (`findAllByIdInAndActiveTrue`).
8. Test đếm SQL: bật `hibernate.generate_statistics` trong test, gọi `GET /api/products?page=0&size=20`, khẳng định `getPrepareStatementCount() ≤ 8`. Lưu ý: các test API hiện có `@Transactional` ở cấp class nên cache entity giữa các lần gọi, test đếm phải xoá persistence context hoặc không `@Transactional`.
9. Nhỏ, làm nếu kịp: 2 request cùng tạo hãng/INCI mới gây 500 (`ProductService.java:208-232`), bắt lỗi trùng rồi đọc lại.
10. **bcrypt của HTTP Basic** (đo 18/11: `/api/conversations` p95 530 ms, DB chỉ 2 câu SQL; request Basic sai mật khẩu cũng mất ~90 ms). `SecurityConfig.passwordEncoder()`: `DelegatingPasswordEncoder` vẫn encode mặc định `bcrypt` (OTP giữ bcrypt), thêm id `sha256-ct` = SHA-256 so sánh bằng `MessageDigest.isEqual`; tài khoản admin trong bộ nhớ lưu `{sha256-ct}…`. Mật khẩu admin vốn ở dạng rõ trong env/bộ nhớ nên bcrypt không bảo vệ thêm (bcrypt chỉ có ích khi kho hash bị lộ). Kỳ vọng p95 `conversations` < 50 ms. Làm **trước** khi bàn index.

## 4. Phần C: ai-brain (T-43.4, #265)
1. Thêm `psycopg-pool` vào `ai-brain/requirements.txt` (ghi phiên bản cố định). Tạo `ConnectionPool(CHATBOT_DB_URL, min_size=1, max_size=4)` khi app khởi động (lifespan trong `app/main.py`), mở lười để pytest dùng stub không cần DB. `run_select` lấy kết nối từ pool, giữ `read_only = True` và `SET statement_timeout = '2s'`.
2. Cache catalog trong bộ nhớ, TTL đọc từ env `CATALOG_CACHE_TTL_SECONDS` (mặc định 60, thêm vào `.env.example`). Có khoá (thread lock) vì FastAPI chạy endpoint sync trong threadpool. Một lần nạp phục vụ cả gợi ý (`load_search_rows`) và danh sách giá (`load_public_catalog`). Vector parse 1 lần lúc nạp.
3. Nạp **đủ** SKU active: vẫn từng trang 100 dòng vì `sql_guard` chặn SELECT quá 100 dòng (giữ luật bảo mật này).
4. Prompt: không đưa 100 dòng đầu nữa. Khi không có hit, đưa top-N (vd. 8) sản phẩm gần câu hỏi nhất (dùng lại cosine đang có). Whitelist giá cho `grounding.py` = giá của đúng các dòng đã đưa cho LLM (hit + top-N), không phải toàn bộ catalog, để không nới luật chống bịa giá (T-06.6).
5. Giá NULL ở một kênh: bỏ giá đó, sản phẩm không có giá ở kênh hiện tại thì không gợi ý ở kênh đó. Không `int(None)`.
6. Log thời gian từng chặng (`db_ms`, `rank_ms`, `llm_ms`) để T-43.2 đo được phần không phải LLM.
7. pytest: stub `run_select` có đếm số lần gọi → 10 lượt chat trong TTL chỉ 1 lần nạp; hết TTL thì nạp lại; dòng giá NULL không làm lỗi; SKU vị trí > 100 giữ đúng giá.
8. **Lỗi đăng nhập DB thì ngừng thử** trong N giây (vd. 60 s) và log mã lỗi Postgres (`sqlstate`, không log URL). Ngày 18/11 mật khẩu `chatbot_reader` sai làm pooler Supabase chặn role (`ECIRCUITBREAKER`) vì mỗi câu chat thử 5 kết nối; log hiện chỉ ghi `OperationalError` nên khó tìm nguyên nhân.
- Số đo 18/11 trên Supabase: phần đọc DB **~10,9 s mỗi câu chat** (p50, catalog 2,2 s + search 8,7 s), local ~0,22 s. Đây là phần lớn độ trễ của bot trên Supabase, không phải LLM.
- Không làm đợt này: xếp hạng bằng pgvector trong SQL (`ORDER BY embedding <=> …`, phải sửa `sql_guard`), Redis.
- ChienPX cũng đang sửa `search.py` (T-10.4, phần `parse_constraints`) và `graph.py` (T-21.7, `system_prompt_for`). Nhắn ChienPX trước khi sửa 2 file này, merge PR của ai xong trước rồi người kia rebase.

## 5. Phần A: chat widget không tải catalog (T-43.3, #264), làm sau PR US-56
Đổi hợp đồng gợi ý từ "text rồi ghép tên" sang "dữ liệu có cấu trúc":
1. ai-brain `ChatResponse` (`app/schemas.py`) thêm `products: list[SuggestedProduct] = []`, mỗi phần tử `{sku, brand, name, price, category}` (giá của kênh hiện tại). `graph.py` điền từ `hits` của `search_product`.
2. Backend `BrainResponse` (`brain/BrainResponse.java`) đọc thêm `products`. `MessageRouter` chỉ gắn vào `BotReply` (`common/BotReply.java`) khi kênh là WEB. Tra 1 query theo danh sách SKU (`findAllBySkuInAndActiveTrue`) để thêm `id` và `imageUrl` (`ProductImages`). SKU không còn active thì bỏ.
3. `WebChatAdapter` gửi `products` trong frame WS. Kênh sàn không đổi (chỉ có text, vẫn qua PolicyEngine).
4. Web: `ChatWidget.tsx` bỏ hẳn vòng tải catalog. Thẻ gợi ý vẽ từ `line.products`, có link `/products/:id`. `suggestionCards.tsx` không cần danh sách sản phẩm nữa (vẫn tách phần chữ còn lại làm lời nhắn).
5. Cập nhật `docs/specs/api-contracts.md` (ChatResponse, BotReply/frame WS) và `web.md`.
6. Test: pytest `ChatResponse.products` khi có hit; backend test `MessageRouter` gắn products cho WEB, không gắn cho SHOPEE/TIKTOK; `WebChatSocketIT` frame có `products`; `npm run build`.
- Đụng code của ChienPX (`schemas.py`, `graph.py`) và TuTM (`BotReply`, `MessageRouter`, `WebChatAdapter`, widget): nhắn 2 bạn trước khi mở PR. PR US-56 (giao diện) cũng sửa `ChatWidget.tsx`, nên làm T-43.3 sau khi PR đó merge. PR US-56 đã tạm đổi widget thành chỉ tải catalog khi mở chat lần đầu; T-43.3 bỏ hẳn phần này.

## 6. Đo trước/sau (T-43.2, #150), làm cuối
- Đo **trước** khi sửa (chạy trên `dev` hiện tại) và **sau** khi xong B, C, A. Ghi kết quả vào `docs/tracking/perf-US-43.md`.
- Script (Python `httpx` hoặc `k6`, đặt trong `bench/perf/`): 200 request, 10 luồng, cho `GET /api/products?page=0..18&size=20`, `GET /api/products/{id}`, `GET /api/conversations?size=20` (seed 10.000 tin nhắn bằng SQL trước). In p50/p95.
- Đếm request: DevTools → Network, lọc `/api/products`. Mở `/` khi chat thu nhỏ: 1 (đã đúng từ US-56). Mở chat lần đầu: trước +4, sau +0.
- ai-brain: lấy `db_ms`, `rank_ms` từ log của 20 câu chat có ràng buộc (vd. "kem chống nắng cho da dầu dưới 300k").
- Đo trên DB local, và trên profile `supabase` nếu có.

## 7. Thứ tự làm và PR
1. T-43.1 (backend), PR riêng, `Closes #149`.
2. T-43.4 (ai-brain), PR riêng, `Closes #265`.
3. T-43.3 (widget + hợp đồng), sau khi PR US-56 merge, `Closes #264`.
4. T-43.2 (đo), `Closes #150` và đóng US-43.
Mỗi PR ghi kết quả test và số đo vào mô tả PR; cập nhật spec trong cùng PR (CLAUDE.md).
