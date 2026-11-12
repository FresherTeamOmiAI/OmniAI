# Mô hình dữ liệu

> Cập nhật: 2026-11-12 · commit 67e63cc · Owner: LucVT (catalog, customer, identity, order, promotion); TuTM (channel_credential, conversation)
> Nguồn: `backend/src/main/resources/db/migration/*.sql`, `backend/.../catalog/*`, `backend/.../customer/*`, `backend/.../channel/credential/*`, `backend/.../conversation/*`

DB: PostgreSQL 16 + pgvector (image `pgvector/pgvector:pg16`). Schema do **Flyway** quản lý, JPA để `ddl-auto: validate`. Vì vậy mọi thay đổi bảng đều phải viết migration mới `V<n>__<mo_ta>.sql`. **Không sửa migration đã merge.**

## Migration

| File | Nội dung |
|---|---|
| `V1__init.sql` | Bảng `product`, `customer`, `channel_identity` |
| `V2__seed_products.sql` | 8 SKU mẫu (Lumi Lab, Sen Việt, Hydra Mint, đều hư cấu). V5 cập nhật lại 8 SKU này |
| `V3__enable_pgvector.sql` | `CREATE EXTENSION vector` |
| `V8__product_embedding.sql` | Cột `product.embedding vector(768)` và view `chatbot_catalog_search` (US-10). Chỉ sản phẩm `active` |
| `V4__catalog_schema_v1.sql` | Schema catalog đợt 1 (T-04.1): thêm `description`, `skin_concerns`, `usage_time`, `updated_at`; NOT NULL cho các trường AC US-04 bắt buộc; CHECK giá/tồn kho ≥ 0, dung tích/PAO > 0; index `category` |
| `V5__import_catalog_80.sql` | Nạp 80 SKU từ `shared/catalog/products.csv` (T-08.2). **Sinh tự động** bằng `tools/catalog/csv_to_sql.py`, không sửa tay. Upsert theo `sku`: 8 SKU của V2 giữ mã/giá và có thêm mô tả, vấn đề da, buổi dùng |
| `V6__chatbot_catalog.sql` | View `chatbot_catalog` và role `chatbot_reader` NOLOGIN. Chỉ tên, thương hiệu, loại da. Không có giá, tồn kho, email |
| `V7__chatbot_catalog_prices.sql` | Thêm `price_web`, `price_shopee`, `price_tiktok` vào view. Vẫn không có tồn kho |
| `V8__channel_credential.sql` | Bảng `channel_credential`: token OAuth2 của shop trên kênh sàn (US-25, decisions #29). Owner: TuTM |
| `V9__product_source_image.sql` | T-47.1: thêm `barcode` (unique), `source_url`, `image_key`, `image_source_url`, `image_license_note` (đều cho phép NULL); **xoá cứng 80 SKU của 5 thương hiệu hư cấu** của V2/V5 (decisions #30) |
| `V10__import_catalog_real.sql` | Nạp 366 SKU thật từ `shared/catalog/products.csv` (T-47.1). **Sinh tự động** bằng `csv_to_sql.py`, upsert theo `sku`, gồm cả cột nguồn/ảnh |
| `V11__conversation.sql` | Bảng `conversation`, `conversation_message`, `message_violation`: lưu hội thoại mọi kênh (US-11, decisions #31). Owner: TuTM |

## Bảng

**product** (entity `catalog/Product.java`). Bắt buộc (NOT NULL) = mọi cột trừ `description`, `skin_concerns`, `usage_time` và 5 cột nguồn/ảnh.
- `sku` unique, dạng `<HÃNG>-<LOAI>-<NN>` (vd. `CERA-CLE-01`; tiền tố hãng ở `BRAND_LIST` trong `tools/catalog/obf_annotate.py`). `name`, `brand` (sản phẩm **thật**, decisions #27, #30).
- `category`: `cleanser, toner, serum, moisturizer, sunscreen, exfoliant, spot-treatment, mask, eye-cream, lip-care`.
- `skin_types`: CSV các giá trị `oily,dry,combination,sensitive,normal`.
- `skin_concerns`: CSV vấn đề da, dùng để lọc/RAG: `acne, oil-control, pores, hydration, dullness, dark-spots, aging, sensitivity, barrier, sun-protection`.
- `usage_time`: `am | pm | am,pm` (CHECK trong DB). Dùng cho routine sáng/tối (US-31).
- `description`: mô tả tiếng Việt ≤ 2000 ký tự (công dụng, kết cấu, cách dùng), cho trang chi tiết và embedding RAG. **Không** ghi giá hay claim y khoa ("trị", "chữa").
- `ingredients_inci`: CSV INCI, tối đa 4000 ký tự. Luật thành phần (`ai-brain/app/rules/ingredients.py`) đọc từ đây.
- `size_ml` > 0, `pao_months` > 0 (dùng được bao lâu sau khi mở).
- Giá theo kênh, đơn vị VND (BIGINT, ≥ 0): `price_web`, `price_shopee`, `price_tiktok`. Kênh sở hữu (WEB) dùng `price_web` (SRS G-15). Đây là giá niêm yết; khuyến mãi để ở bảng riêng (US-33).
- Đơn DEMO trên web không trừ `stock` (SRS G-04). Đơn từ sàn về sẽ trừ (US-51, S5). Admin không bao giờ xoá cứng sản phẩm, chỉ đặt `active=false` (G-07). Ngoại lệ duy nhất: V9 xoá dữ liệu seed hư cấu (decisions #30).
- `stock` ≥ 0, `active` (false = ẩn khỏi store và AI không được gợi ý). `updated_at` tự cập nhật khi sửa qua JPA (`@PreUpdate`).
- Nguồn và ảnh (V9): `barcode` (mã trên Open Beauty Facts, unique), `source_url`, `image_key` (`<SKU>.webp` trong bucket Supabase, decisions #26), `image_source_url` (ảnh gốc), `image_license_note`. Sản phẩm admin tạo qua API để NULL; admin sửa không làm mất (không có trong `ProductRequest`). `imageUrl` không phải cột: `catalog/ProductImages.java` (entity listener) ghép từ `omniai.images.base-url` + `bucket` + `image_key`.
- `embedding vector(768)` (V12, US-10), để trống đến khi chạy `python -m app.rag.index_catalog`. JPA không map cột này. View `chatbot_catalog_search` đọc embedding và `description` cho RAG.

**customer** (`customer/Customer.java`): khách hợp nhất. `display_name`, `email` unique, `phone` unique, `created_at`.

**channel_identity** (`customer/ChannelIdentity.java`): một danh tính trên một kênh.
- `customer_id` FK, có thể null khi chưa liên kết. `channel` (enum `ChannelType`), `external_id`. Unique (`channel`, `external_id`).
- `linked_by`: `OTP | DEEP_LINK | LINK_CODE | ORDER_CLAIM | PROBABILISTIC_CONFIRMED`. `DEEP_LINK` còn trong enum nhưng không dùng nữa sau khi bỏ Telegram (decisions #22).
- `confidence`: 1.0 nếu liên kết tất định, nhỏ hơn 1 nếu đến từ gợi ý xác suất đã được khách xác nhận.
- `consent_at`.
- Nguyên tắc: chỉ gắn vào customer khi có bằng chứng + consent (Luật BVDLCN 91/2025). **Không bao giờ tự gộp.**

**channel_credential** (`channel/credential/ChannelCredential.java`, owner TuTM): ủy quyền OAuth2 của 1 shop trên 1 kênh sàn. TikTok Shop dùng từ S2, Shopee dùng chung.
- `channel` (enum `ChannelType`), `shop_id`; unique (`channel`, `shop_id`). `shop_name`, `shop_cipher` (TikTok: tham số `shop_cipher` của mọi API dữ liệu shop).
- `access_token`, `access_expires_at`, `refresh_token`, `refresh_expires_at` (NOT NULL). `scopes`: CSV scope đã cấp. `updated_at`.
- `status`: `ACTIVE | NEEDS_REAUTH | REVOKED` (CHECK). `NEEDS_REAUTH` khi refresh token hết hạn, `REVOKED` khi nhận webhook SELLER_DEAUTHORIZATION. Khác `ACTIVE` thì hub không gọi API sàn.
- Token đang lưu **dạng rõ** vì là khóa test của simulator. Nối sàn thật thì phải mã hóa 2 cột token.

**conversation** (`conversation/Conversation.java`, owner TuTM): 1 hội thoại trên 1 kênh. Ghi bởi `ConversationService` mỗi khi `MessageRouter` nhận/trả tin.
- `channel`, `external_id` (= `CanonicalMessage.conversationId`, vd. `web:<session>`, `tiktok:<conversation_id>`); unique (`channel`, `external_id`). Tạo bằng upsert `ON CONFLICT` nên 2 tin đầu đến cùng lúc không tạo 2 dòng.
- `channel_user_id`, `customer_id` (FK `customer`, null khi chưa liên kết danh tính), `started_at`, `last_message_at` (không lùi khi tin đến trễ), `message_count` (số dòng IN + OUT). Index (`channel`, `last_message_at` DESC).

**conversation_message** (`conversation/ConversationMessage.java`): 1 tin. `direction` `IN | OUT` (CHECK).
- `message_id`: IN là messageId tin khách, OUT là messageId của tin mà bot trả lời. Unique (`conversation_id`, `direction`, `message_id`): kênh gửi lại cùng tin thì không thêm dòng.
- `text`: IN = tin khách, OUT = `finalText` (câu khách nhận sau policy). `raw_text`: chỉ OUT, câu gốc của AI Brain. `blocked`, `handoff`, `created_at` (IN = `receivedAt` của kênh, OUT = lúc trả lời).

**message_violation** (`@ElementCollection` `StoredViolation` của `ConversationMessage`): (`message_id`, `position`) PK, `rule_id`, `action` `BLOCK | REDACT | REPLACE` (CHECK), `matched`. Xóa tin thì xóa theo (CASCADE).

## Dữ liệu catalog (`shared/catalog/products.csv`)

- **Catalog thật** (T-47.1): 366 SKU chăm sóc da mặt của 50 hãng có bán ở VN, lấy từ Open Beauty Facts. Dữ liệu ODbL, ảnh CC BY-SA 3.0, **bắt buộc ghi nguồn** khi hiển thị. Đủ 10 danh mục; 2 SKU ẩn (`NEUT-SUN-02`, `NIVE-MSK-01`), 4 SKU hết hàng. Chi tiết, số liệu, giấy phép và cách chạy: [../catalog/du-lieu-san-pham-that.md](../catalog/du-lieu-san-pham-that.md).
- **File sinh tự động**: `products.csv` do `tools/catalog/obf_fetch.py build` sinh từ `shared/catalog/real/selection.csv`. Không sửa tay `products.csv`, chỉ sửa `selection.csv`.
  - `selection.csv` giữ các cột do shop tự đặt: SKU, tên, danh mục, loại da, vấn đề da, buổi dùng, PAO dự phòng, giá, tồn kho, ẩn/hiện, mô tả, `annotated_by`.
  - `annotated_by=manual` là 80 dòng do người soạn và duyệt; `auto` là dòng `discover` tự soạn theo quy tắc trong `obf_annotate.py`.
  - Ảnh WebP nằm ở `shared/catalog/real/images/` (gitignore).
- Cột = cột bảng `product` (trừ `id`, `updated_at`), cùng thứ tự, rồi thêm `barcode, source_url, image_key, image_source_url, image_license_note`. UTF-8, phân cách `,`, ô có dấu phẩy được đặt trong `"..."`. `active` là `true|false`.
- INCI lấy từ OBF qua `normalize_inci()`. Không dùng tên INCI có dấu phẩy: `1,2-Hexanediol` được đổi thành `1.2-Hexanediol`, vì cột INCI tách theo dấu phẩy. INCI do cộng đồng nhập nên còn lỗi chính tả.
- Nhóm hoạt chất của `ingredients.py`: retinoid 11, AHA 19, BHA 55, vitamin C 46, azelaic 2, **BPO 0**.
- Giá do shop tự đặt (không phải giá thật): Shopee ≈ web + 4%, TikTok ≈ web − 3%, làm tròn nghìn đồng.
- Kiểm tra: `python tools/catalog/validate_catalog.py [file.csv]` (chỉ dùng thư viện chuẩn, mã thoát 1 nếu lỗi). Script kiểm header (cũ, hoặc có thêm `SOURCE_COLUMNS`), ≥ 80 SKU, trùng SKU, trường bắt buộc, bộ từ vựng, số hợp lệ, claim y khoa trong tên/mô tả; với cột nguồn/ảnh thì kiểm barcode, `image_key = <SKU>.webp`, URL https, ghi chú giấy phép.
- Import vào DB: `python tools/catalog/csv_to_sql.py V<n>__<mo_ta>.sql` chạy validator rồi ghi migration upsert vào `backend/src/main/resources/db/migration/`. Script từ chối ghi đè file đã có. Sửa catalog sau khi V10 đã merge thì sinh **file V<n> mới** (upsert nên chạy lại cả CSV được). Không xoá SKU: muốn bỏ SKU thì đặt `active=false`.
- Backend test đọc số SKU mong đợi từ CSV này (`backend/src/test/.../TestCatalog.java`).

## Repository
- `ProductRepository` (+ `JpaSpecificationExecutor` cho tìm kiếm admin): `findByActiveTrue`, `findByActiveTrueAndSkinTypesContainingIgnoreCase`, `findByIdAndActiveTrue` (store, G-06), `existsBySku`.
- Ràng buộc unique của `sku` tên là `product_sku_key` (Postgres tự đặt ở V1). `ProductService` dựa vào tên này để trả 409 khi 2 request tạo cùng SKU một lúc.
- `CustomerRepository`: `findByEmail`, `findByPhone`.
- `ChannelIdentityRepository`: `findByChannelAndExternalId`, `findByCustomerId`.
- `ChannelCredentialRepository`: `findByChannelAndShopId`, `findByChannelOrderByShopId`.
- `ConversationRepository`: `upsert` (native `INSERT ... ON CONFLICT ... RETURNING id`), `touch` (tăng `message_count`, dời `last_message_at`), `findByChannel`.
- `ConversationMessageRepository`: `existsByConversationIdAndDirectionAndMessageId`, `findByConversationId` (phân trang).

## Dự kiến (chưa có)
OTP/JWT (US-13, S3, chỉ email), mã liên kết `OMNI-XXXXXX` 6 ký tự kèm đếm số lần sai theo danh tính kênh (US-22, S4, SRS G-01), order (US-09/23), promotion/voucher/lịch sử giá (US-33, S5, mốc giờ theo `Asia/Ho_Chi_Minh`), consent log (US-30, S7). Cho trụ E (decisions #23): trạng thái đồng bộ theo kênh (US-49, S3), đơn từ sàn (US-51, S5), trạng thái duyệt sản phẩm (US-54, S7), đánh giá sản phẩm (US-55, S8).

## Test
`OmniAiApplicationTests` chạy Flyway trên Postgres thật (Testcontainers) và kiểm tra catalog đủ 80 SKU (78 active, 4 hết hàng), SKU của V2 được upsert, extension vector, cùng các ràng buộc V4 (giá âm, `usage_time` sai, thiếu giá kênh đều bị DB từ chối). Vì vậy cần Docker. `channel/tiktok/TiktokWebhookIT` lưu và đọc lại `channel_credential` (V8). `conversation/ConversationStoreIT` kiểm V11 qua `MessageRouter` thật: IN/OUT, vi phạm, chống trùng, mỗi kênh 1 hội thoại.
