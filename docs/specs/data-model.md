# Mô hình dữ liệu

> Cập nhật: 2026-11-20 · T-27.4 V24 chuẩn hoá tên INCI theo EU/ASEAN, bảng `ingredient_alias` (decisions #54); 2026-11-20 · T-33.1 V23 khuyến mãi (`promotion`, `promotion_item`) và `price_history` (decisions #53); 2026-11-19 · T-23.1 V22 `customer_order.buyer_external_id`, đơn sàn tra theo danh tính đã liên kết (decisions #52); 2026-11-19 · job dọn `otp_challenge`, `link_code` (trừ CONFIRMED), `link_code_attempt` cũ hơn 7 ngày (decisions #51); 2026-11-18 · T-22.1 V21 mã liên kết (`link_code`, `link_code_attempt`); T-19.3 V20 hồ sơ da (`skin_profile` + 2 bảng con); T-21.5, T-21.6 simulator Shopee tách ra `simulators/shopee` (:8091, trang `/ui`); 2026-11-18 · T-50.2 V19 `aspect` cho `product_channel_sync`; 2026-11-18 · T-49.1 V18 `product_channel_sync`; T-25.4 V17 trạng thái gửi câu bot (`delivery_status`); 2026-11-13 · T-04.1 V15 chuẩn hoá product (decisions #35); T-09.3 V14 `customer_order`; T-09.2 store lọc qua Specification; T-04.1 tách giá theo kênh (V13, decisions #32) · Owner: LucVT (catalog, customer, identity, order, promotion); TuTM (channel_credential, conversation)
> Nguồn: `backend/src/main/resources/db/migration/*.sql`, `backend/.../catalog/*`, `backend/.../customer/*`, `backend/.../channel/credential/*`, `backend/.../conversation/*`

DB: PostgreSQL 16 + pgvector (image `pgvector/pgvector:pg16`). Schema do **Flyway** quản lý, JPA để `ddl-auto: validate`. Vì vậy mọi thay đổi bảng đều phải viết migration mới `V<n>__<mo_ta>.sql`. **Không sửa migration đã merge.**

## Migration

| File | Nội dung |
|---|---|
| `V1__init.sql` | Bảng `product`, `customer`, `channel_identity` |
| `V2__seed_products.sql` | 8 SKU mẫu (Lumi Lab, Sen Việt, Hydra Mint, đều hư cấu). V5 cập nhật lại 8 SKU này |
| `V3__enable_pgvector.sql` | `CREATE EXTENSION vector` |
| `V4__catalog_schema_v1.sql` | Schema catalog đợt 1 (T-04.1): thêm `description`, `skin_concerns`, `usage_time`, `updated_at`; NOT NULL cho các trường AC US-04 bắt buộc; CHECK giá/tồn kho ≥ 0, dung tích/PAO > 0; index `category` |
| `V5__import_catalog_80.sql` | Nạp 80 SKU từ `shared/catalog/products.csv` (T-08.2). **Sinh tự động** bằng `tools/catalog/csv_to_sql.py`, không sửa tay. Upsert theo `sku`: 8 SKU của V2 giữ mã/giá và có thêm mô tả, vấn đề da, buổi dùng |
| `V6__chatbot_catalog.sql` | View `chatbot_catalog` và role `chatbot_reader` NOLOGIN. Chỉ tên, thương hiệu, loại da. Không có giá, tồn kho, email |
| `V7__chatbot_catalog_prices.sql` | Thêm `price_web`, `price_shopee`, `price_tiktok` vào view. Vẫn không có tồn kho |
| `V8__channel_credential.sql` | Bảng `channel_credential`: token OAuth2 của shop trên kênh sàn (US-25, decisions #29). Comment đầu file ghi "decisions #27" là số cũ; **không sửa file** vì đổi nội dung migration đã chạy làm Flyway báo sai checksum (T-25.5). Owner: TuTM |
| `V9__product_source_image.sql` | T-47.1: thêm `barcode` (unique), `source_url`, `image_key`, `image_source_url`, `image_license_note` (đều cho phép NULL); **xoá cứng 80 SKU của 5 thương hiệu hư cấu** của V2/V5 (decisions #30) |
| `V10__import_catalog_real.sql` | Nạp 366 SKU thật từ `shared/catalog/products.csv` (T-47.1). **Sinh tự động** bằng `csv_to_sql.py`, upsert theo `sku`, gồm cả cột nguồn/ảnh |
| `V11__conversation.sql` | Bảng `conversation`, `conversation_message`, `message_violation`: lưu hội thoại mọi kênh (US-11, decisions #31). Owner: TuTM |
| `V12__product_embedding.sql` | Cột `product.embedding vector(768)`, index HNSW cosine, view `chatbot_catalog_search` (US-10). Chỉ sản phẩm `active`. Owner: ChienPX |
| `V13__product_channel_price.sql` | T-04.1 sửa theo review: **tách giá theo kênh khỏi `product`**. Thêm `sales_channel` (WEB, SHOPEE, TIKTOK) và `product_channel` (1 dòng / sản phẩm / kênh), chép giá 366 SKU × 3 kênh, xoá 3 cột `price_*` và CHECK `ck_product_prices`. Tạo lại 2 view chatbot với đúng tên cột cũ (decisions #32) |
| `V14__demo_order.sql` | T-09.3: bảng `customer_order` + `customer_order_item` (đơn mọi kênh; S2 chỉ có đơn `DEMO` trên web, không trừ tồn kho, decisions #34) |
| `V16__otp_challenge.sql` | T-13.1: bảng `otp_challenge` (mã OTP đăng nhập người mua, chỉ lưu hash, US-13, decisions #39) |
| `V15__normalize_product.sql` | T-04.1 sửa theo review (phần A+B, decisions #35): **chuẩn hoá bảng `product`**. Bảng tra cứu `brand`, `category`, `skin_type`, `skin_concern`, `ingredient`; bảng nối `product_skin_type`, `product_skin_concern`, `product_ingredient` (giữ thứ tự INCI); `product_image`; `use_am`/`use_pm` thay `usage_time`; `created_at`. Xoá 8 cột `brand, skin_types, skin_concerns, usage_time, ingredients_inci, image_key, image_source_url, image_license_note`. Tạo lại 2 view chatbot **giữ nguyên cột** qua view nội bộ `product_text_pivot` |
| `V17__message_delivery.sql` | T-25.4: thêm `delivery_status`, `delivery_attempts`, `delivery_error` vào `conversation_message` (trạng thái gửi câu bot ra kênh gửi async, TikTok). Owner: TuTM |
| `V18__product_channel_sync.sql` | T-49.1: bảng `product_channel_sync` (hàng đợi + trạng thái đồng bộ sản phẩm ra từng sàn, decisions #42). Owner: TuTM |
| `V19__product_channel_sync_aspect.sql` | T-50.2: thêm cột `aspect` (`PRODUCT | PRICE | STOCK`) vào `product_channel_sync`, khoá chính thành (`product_id`, `channel_code`, `aspect`). Số V16 để trống cho PR #273. Owner: TuTM |
| `V20__skin_profile.sql` | T-19.3: bảng `skin_profile`, `skin_profile_concern`, `skin_profile_allergy` (hồ sơ da, US-19, decisions #48). Owner: LucVT |
| `V24__normalize_inci.sql` | T-27.4: bảng `ingredient_alias` (tên gốc viết thường -> tên chuẩn EU/ASEAN, NULL = rác) và chuyển dữ liệu **tại chỗ**: đổi `product_ingredient` sang tên chuẩn, bỏ rác và trùng, đánh lại `position`, xoá tên cũ không dùng, sửa cách viết hoa, gắn lại `active_group`. **Sinh tự động** bởi `tools/catalog/normalize_inci.py`. US-27, decisions #54. Owner: LucVT |
| `V23__promotion.sql` | T-33.1: bảng `promotion` (voucher, flash sale theo kênh), `promotion_item` (giá sale theo SKU), `price_history` (lịch sử giá niêm yết, chép sẵn giá hiện có làm mốc đầu). US-33, decisions #53. Owner: LucVT |
| `V22__customer_order_buyer.sql` | T-23.1: cột `customer_order.buyer_external_id` (người mua phía sàn), CHECK đơn WEB không có người mua sàn, index một phần (`channel_code`, `buyer_external_id`). US-23, decisions #52. Owner: LucVT |
| `V21__link_code.sql` | T-22.1: bảng `link_code` (mã OMNI, chỉ lưu SHA-256) và `link_code_attempt` (lần gõ mã thất bại theo danh tính kênh), US-22, decisions #49. Owner: LucVT |

## Bảng

**product** (entity `catalog/Product.java`). Sau V15 bảng chỉ còn giá trị đơn, mọi giá trị nhiều phần tử ở bảng riêng (decisions #35).
- Cột: `id`, `sku` unique (dạng `<HÃNG>-<LOAI>-<NN>`, vd. `CERA-CLE-01`), `name`, `brand_id` FK `brand`, `category` FK `category(code)`, `description` (tiếng Việt ≤ 2000 ký tự, **không** ghi giá hay claim y khoa), `size_ml` > 0, `pao_months` > 0, `stock` ≥ 0, `active`, `use_am`, `use_pm` (buổi dùng, cả 2 false = chưa ghi), `barcode` unique (Open Beauty Facts), `source_url`, `embedding vector(768)` (V12, JPA không map), `created_at`, `updated_at`.
- **Giá** ở `product_channel` (V13, decisions #32). **Loại da / vấn đề da** ở `product_skin_type` / `product_skin_concern`. **INCI** ở `product_ingredient`. **Ảnh** ở `product_image`.
- **JSON API giữ hình dạng cũ** qua getter `@JsonProperty` của entity: `brand` (tên hãng), `skinTypes`/`skinConcerns` (mã nối dấu phẩy, **theo `sort_order`** của bảng tra cứu), `usageTime` (`am | pm | am,pm | null` từ 2 cờ), `ingredientsInci` (tên nối `", "` theo `position`), `imageKey`/`imageSourceUrl`/`imageLicenseNote` (ảnh chính). Web, Postman, ai-brain không phải đổi.
- Collection của entity: `@ManyToOne brandRef`, `@ManyToMany skinTypeRefs`/`skinConcernRefs` (`@OrderBy sortOrder`), `@ManyToMany ingredients` (`@OrderColumn position`, bắt đầu 1), `@ElementCollection images` (`@OrderColumn sort_order`), `prices`. Tất cả EAGER + `@Fetch(SELECT)` + `@BatchSize(50)`: không nhân bản dòng, không 1 query / sản phẩm.
- Ghi (admin, `ProductService`): hãng tìm theo tên không phân biệt hoa thường, chưa có thì thêm (`code` = tiền tố SKU nếu còn trống). INCI bỏ trùng không phân biệt hoa thường, thành phần chưa có thì thêm vào `ingredient`. Bảng nối được gán set/list **mới** khi đổi (Hibernate xoá rồi chèn lại, không vướng UNIQUE tạm thời). Chỉ đổi bảng con thì vẫn cập nhật `updated_at`.
- Từ vựng (danh mục, loại da, vấn đề da) **đọc từ bảng tra cứu**, DB là nguồn chuẩn; `CatalogVocabulary.java` chỉ còn luật SKU, claim y khoa, buổi dùng, `MIN_INCI`. `tools/catalog/validate_catalog.py` (offline) giữ bản riêng, phải khớp bảng.
- Đơn DEMO trên web không trừ `stock` (SRS G-04). Đơn từ sàn về sẽ trừ (US-51, S5). Admin không bao giờ xoá cứng sản phẩm, chỉ đặt `active=false` (G-07). Ngoại lệ duy nhất: V9 xoá dữ liệu seed hư cấu (decisions #30).
- `imageUrl` không phải cột: `catalog/ProductImages.java` (entity listener) ghép từ `omniai.images.base-url` + `bucket` + khoá ảnh chính.

**brand** (`catalog/Brand.java`, V15): `id`, `code` unique (tiền tố SKU, vd. `CERA`; NULL khi không xác định được duy nhất), `name` (unique index `lower(name)`). Catalog thật: 50 hãng, đều có `code`.

**category / skin_type / skin_concern** (`Category`, `SkinType`, `SkinConcern`, V15): bảng tra cứu `code` PK, `name_vi`, `sort_order`. 10 danh mục, 5 loại da (`oily, dry, combination, sensitive, normal`), 10 vấn đề da (`acne, oil-control, pores, hydration, dullness, dark-spots, aging, sensitivity, barrier, sun-protection`). Nhãn tiếng Việt khớp `web/src/shared/vocabulary.ts`.

**product_skin_type / product_skin_concern** (V15): PK (`product_id`, `skin_type_code` / `skin_concern_code`), FK CASCADE theo sản phẩm, FK tới bảng tra cứu (mã lạ bị DB chặn), index theo mã để lọc store.

**ingredient** (`catalog/Ingredient.java`, V15): `id`, `inci_name` ≤ 200 ký tự (unique index `lower(inci_name)`, lưu cách viết gặp đầu tiên), `active_group` CHECK `retinoid | aha | bha | bpo | vitamin_c | azelaic` (nhóm của luật thành phần `ai-brain/app/rules/ingredients.py`). Từ V24 (T-27.4) mọi tên là **tên chuẩn EU/ASEAN** (`Aqua`, `Parfum`, `Aroma`, `Paraffinum Liquidum`, `Cera Alba`, `Mel`, `CI xxxxx`, `(nano)`; `1.2-Hexanediol` giữ dấu chấm vì cột INCI tách bằng dấu phẩy), không còn mã lô, %, ppm. Catalog thật: 1.375 thành phần (trước V24: 1.932); retinoid 9, AHA 19, BHA 45, vitamin C 7, azelaic 2 SKU (gán theo đúng tên INCI của ai-brain, **chưa tính dẫn xuất**, decisions #54).

**ingredient_alias** (`catalog/IngredientAlias.java`, V24, chỉ đọc): PK (`alias_lower`, `seq`), `canonical` (NULL = token rác: mã lô F.I.L, mảnh vỡ OCR; 1 token dính nhiều tên thì nhiều dòng theo `seq`). Mọi tên chuẩn cũng là bí danh của chính nó. Nguồn: `shared/catalog/inci_aliases.csv`, sinh từ luật trong `tools/catalog/normalize_inci.py` + ghi đè tay `shared/catalog/inci_overrides.csv`. `ProductService` dùng bảng này để đổi tên admin gõ sang tên chuẩn.

**product_ingredient** (V15): PK (`product_id`, `position` ≥ 1), unique (`product_id`, `ingredient_id`), FK CASCADE. Giữ **thứ tự trên nhãn** (nồng độ giảm dần). 5 SKU có thành phần trùng trong CSV (NEUT-SUN-02, CETA-CLE-03, CETA-MOI-03, GARN-SUN-02, NUXE-MOI-02) được bỏ trùng, giữ lần đầu.

**product_image** (`@ElementCollection` `ProductImage`, V15): PK (`product_id`, `sort_order` ≥ 0), `storage_key` unique (`<SKU>.webp` trong bucket Supabase, decisions #26), `source_url` (ảnh gốc), `license_note`, `is_primary` (unique index một phần: tối đa 1 ảnh chính / sản phẩm). Admin chưa sửa ảnh qua API.

**sales_channel** (`catalog/SalesChannel.java`, V13): danh sách kênh bán.
- `code` PK (`WEB | SHOPEE | TIKTOK`, khớp enum `ChannelType`; CHECK chữ hoa/số/`_`), `name`, `owned` (kênh shop sở hữu, chỉ `WEB`; SRS G-15), `active` (false = tạm ngừng bán trên kênh, giữ dữ liệu giá).
- **Thêm sàn = INSERT 1 dòng** + giá cho các sản phẩm, không ALTER bảng nào. Code kênh (adapter) và cột trong 2 view chatbot thì vẫn phải thêm.

**product_channel** (`@ElementCollection` `prices` của `Product`, V13): giá niêm yết của 1 sản phẩm trên 1 kênh.
- PK (`product_id`, `channel_code`). `product_id` FK `product` ON DELETE CASCADE, `channel_code` FK `sales_channel`, `price` BIGINT VND, CHECK ≥ 0. Index `channel_code`.
- AC US-04 "có giá cho mọi kênh" **không ép bằng ràng buộc DB được** nữa: `ProductService` bắt buộc đủ giá cho mọi kênh `active` (400 ở field `prices[<mã>]`), từ chối mã kênh lạ. Kênh không active thì không bắt buộc, admin sửa không làm mất giá cũ của kênh đó.
- Bảng sẽ có thêm cột theo kênh khi cần: mã sản phẩm phía sàn, trạng thái đồng bộ (US-49). Khuyến mãi để bảng riêng (`promotion`, V23).
- View nội bộ `product_text_pivot` (V15) ghép lại `brand`, `skin_types`, `skin_concerns`, `usage_time` thành chuỗi như cũ. View `product_price_pivot` (nội bộ, không cấp cho `chatbot_reader`) xoay giá thành `price_web, price_shopee, price_tiktok` cho `chatbot_catalog` và `chatbot_catalog_search`, nên ai-brain đọc như cũ.

**customer_order** (`order/CustomerOrder.java`, V14, owner LucVT): 1 đơn hàng, dùng chung mọi kênh để US-23 (lịch sử đơn) và US-51 (đơn từ sàn) dùng tiếp.
- `channel_code` FK `sales_channel`; `external_order_id` (mã đơn sàn, web = NULL), unique (`channel_code`, `external_order_id`); `customer_id` FK `customer`, NULL ở S2 vì đặt không cần đăng nhập (G-04).
- `buyer_external_id` (V22): người mua phía sàn (Shopee `buyer_user_id`, TikTok `user_id`); đơn WEB phải NULL (CHECK `ck_customer_order_buyer`).
- **Sở hữu đơn (decisions #52):** đơn WEB thuộc `customer_id`. Đơn sàn để `customer_id` NULL, thuộc khách C khi `channel_identity (channel = channel_code, external_id = buyer_external_id)` đang gắn với C, tra lúc đọc (`CustomerOrderRepository.findHistoryIds`). Liên kết muộn vẫn thấy đơn cũ, gỡ liên kết thì đơn ẩn ngay.
- `status` CHECK chỉ `DEMO` (đơn web thử nghiệm: không thanh toán, không giao, **không trừ `stock`**). US-51 mở rộng CHECK và có trừ tồn kho. `total_amount` >= 0, `created_at`.

**customer_order_item** (`@ElementCollection` `OrderItem` của `CustomerOrder`): PK (`order_id`, `line_no` từ 1), FK `order_id` CASCADE, `product_id` FK `product`, unique (`order_id`, `product_id`).
- **Chụp lại lúc đặt:** `sku`, `product_name`, `unit_price` (giá web từ `product_channel`). Sản phẩm đổi giá/tên sau này thì đơn cũ không đổi.
- `quantity` CHECK 1..10; `line_total` CHECK = `unit_price * quantity`.

**customer** (`customer/Customer.java`): khách hợp nhất. `display_name`, `email` unique, `phone` unique, `created_at`.

**channel_identity** (`customer/ChannelIdentity.java`): một danh tính trên một kênh.
- `customer_id` FK, có thể null khi chưa liên kết. `channel` (enum `ChannelType`), `external_id`. Unique (`channel`, `external_id`).
- `linked_by`: `OTP | DEEP_LINK | LINK_CODE | ORDER_CLAIM | PROBABILISTIC_CONFIRMED`. `DEEP_LINK` còn trong enum nhưng không dùng nữa sau khi bỏ Telegram (decisions #22).
- `confidence`: 1.0 nếu liên kết tất định, nhỏ hơn 1 nếu đến từ gợi ý xác suất đã được khách xác nhận.
- `consent_at`.
- Kênh WEB (US-13): `external_id` = `customer.id` (chuỗi), `linked_by=OTP`, `confidence=1.0`, `consent_at` = lúc xác thực OTP lần đầu (khách đã tick đồng ý). Tạo bởi `identity/WebIdentityLinker`. Hội thoại web của khách đã đăng nhập có `conversation.customer_id` và `external_id = web:c<customerId>`.
- Kênh sàn (US-22, T-22.1): `external_id` = id người mua trên sàn (`CanonicalMessage.channelUserId`), `linked_by=LINK_CODE`, `confidence=1.0`, `consent_at` = lúc khách xác nhận trên web. Tạo/gắn bởi `identity/LinkCodeService.confirm`. Danh tính đã gắn khách khác thì từ chối (409).
- Nguyên tắc: chỉ gắn vào customer khi có bằng chứng + consent (Luật BVDLCN 91/2025). **Không bao giờ tự gộp.**

**skin_profile** (`profile/SkinProfile.java`, V20, owner LucVT): hồ sơ da, 1 khách 1 dòng, dùng chung mọi kênh đã liên kết (SRS UC-13).
- `customer_id` PK, FK `customer` ON DELETE CASCADE (xoá khách là xoá hồ sơ). `skin_type` FK `skin_type(code)`, null được.
- `pregnant` BOOLEAN: mang thai hoặc cho con bú. `source` `FORM | CHAT` (CHECK). `health_consent_at` NOT NULL (lần đồng ý đầu), `updated_at`.
- `skin_profile_concern` (`customer_id`, `concern_code` FK `skin_concern`), PK cả 2 cột. `skin_profile_allergy` (`customer_id`, `position` 1–10, `name` ≤ 60), PK (`customer_id`, `position`); bỏ trùng ở backend, không có unique index (Hibernate cập nhật theo vị trí). Cả 2 CASCADE theo `skin_profile`.

**link_code** (`identity/LinkCode.java`, V21, owner LucVT): 1 lần khách lấy mã OMNI.
- `customer_id` FK CASCADE, `code_hash` unique (SHA-256 hex của `OMNI-XXXXXX`, không lưu mã gốc), `created_at`, `expires_at` (+15 phút), `revoked_at` (lấy mã mới thì mã chưa ai gõ bị thu hồi).
- `claimed_channel` (`SHOPEE | TIKTOK`), `claimed_external_id`, `claimed_at`: danh tính sàn đã gõ mã (cả 3 cùng null hoặc cùng có). Hạn xác nhận = `claimed_at` + 15 phút.
- `resolution` (`CONFIRMED | REJECTED`) + `resolved_at`. Index (`customer_id`, `created_at` DESC).
- `IdentityCleanup` xoá hằng đêm các dòng `created_at` cũ hơn 7 ngày, **trừ** `CONFIRMED` (giữ làm vết liên kết, mỗi lần liên kết 1 dòng).

**link_code_attempt** (`identity/LinkCodeAttempt.java`, V21): 1 lần gõ mã thất bại (`channel`, `external_id`, `attempted_at`). Đủ 5 dòng trong 1 giờ thì danh tính bị khoá nhập mã. `IdentityCleanup` xoá hằng đêm dòng cũ hơn 7 ngày.

**promotion** (`promotion/Promotion.java`, V23, owner LucVT): 1 khuyến mãi của 1 kênh (US-33, decisions #53).
- `channel_code` FK `sales_channel`, `type` `VOUCHER | FLASH_SALE` (CHECK), `name`, `starts_at`, `ends_at` (TIMESTAMPTZ, CHECK `ends_at > starts_at`), `enabled` (tắt tay), `created_at`, `updated_at`. Index (`channel_code`, `ends_at`).
- VOUCHER: `voucher_code` (UNIQUE `uq_promotion_voucher_code` theo kênh; `PromotionService` dựa vào tên này để trả 409), `discount_amount` > 0 (VND), `min_order_amount` ≥ 0. FLASH_SALE: 3 cột này NULL (CHECK `ck_promotion_voucher`).
- Không có cột trạng thái: `Promotion.status(now)` tính `SCHEDULED | ACTIVE | EXPIRED | DISABLED`, nên hết hạn thì tự không được trả, không cần job.

**promotion_item** (`@ElementCollection` `Promotion.items`, V23): PK (`promotion_id`, `product_id`), `sale_price` > 0. Chỉ flash sale có dòng. Giá sale < giá niêm yết và không chồng flash sale cùng SKU, cùng kênh, giao thời gian: kiểm ở `PromotionService`, không ép bằng DB.

**price_history** (`promotion/PriceHistory.java`, V23): 1 lần đổi giá niêm yết của 1 sản phẩm ở 1 kênh. `old_price` (NULL = giá đầu tiên), `new_price`, `changed_at`. Index (`product_id`, `channel_code`, `changed_at` DESC). Ghi bởi `PriceHistoryRecorder` khi nhận `catalog.ProductPricesChanged` (cùng transaction với lần sửa giá); V23 chép giá hiện có làm mốc đầu. Sửa giá bằng SQL tay thì không có dòng.

**otp_challenge** (`identity/OtpChallenge.java`, V16, owner LucVT): 1 lần gửi OTP tới 1 email.
- `email` (đã trim + lowercase), `code_hash` (bcrypt qua `PasswordEncoder`, không lưu mã gốc), `created_at`, `expires_at` (+5 phút), `attempts` (số lần sai), `locked_until` (sai đủ 5 lần thì +15 phút), `consumed_at` (đã đăng nhập bằng mã này). Index (`email`, `created_at` DESC).
- Dòng mới nhất của email là mã hiệu lực. Giới hạn gửi lại tính trên các dòng: dòng mới nhất < 60 s, hoặc ≥ 5 dòng trong 1 giờ. `IdentityCleanup` xoá hằng đêm dòng `created_at` cũ hơn 7 ngày (đã hết hạn, hết khoá, ngoài cửa sổ 1 giờ).

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
- `delivery_status` (V17, CHECK `RETRYING | SENT | FAILED`, NULL = kênh trả lời đồng bộ như web/simulator API, hoặc tin IN), `delivery_attempts` (số lần đã gửi), `delivery_error` (lỗi lần gần nhất, ≤ 1000 ký tự). `FAILED` thì `handoff = TRUE`: hàng chờ cho nhân viên (US-35), có index một phần theo `created_at`. Sàn nhận câu khác finalText (fallback khi TikTok báo `45101006`) thì `text` được ghi đè bằng câu đó.

**product_channel_sync** (`sync/ProductSyncState.java`, V18, T-49.1; V19 T-50.2): PK (`product_id`, `channel_code`, `aspect`), `aspect` `PRODUCT | PRICE | STOCK` (PRICE hash = giá của kênh, STOCK hash = tồn; `external_id` nằm ở dòng PRODUCT), FK CASCADE theo sản phẩm, FK `sales_channel`. `status` `PENDING | SYNCED | FAILED` (CHECK). `content_hash` = SHA-256 nội dung cần có trên sàn, `synced_hash` = của bản đã gửi thành công: bằng nhau thì sự kiện mới là trùng, không gửi. Hash gồm tên, mô tả, hãng, loại, INCI, dung tích, khoá ảnh, active; **không** gồm giá, tồn (US-50). `external_id` (mã phía sàn), `attempts`, `next_attempt_at` (lúc gửi/thử lại; đang gửi thì bị dời thêm `lease`), `last_error`, `synced_at`, `updated_at`. Index một phần `next_attempt_at WHERE status = 'PENDING'`. Tách khỏi `product_channel` vì bảng đó là `@ElementCollection`, Hibernate ghi lại cả bảng con khi đổi giá.

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
- `SalesChannelRepository`: `findAllByOrderByOwnedDescCodeAsc` (kênh sở hữu trước).
- `BrandRepository` (`findByNameIgnoreCase`, `existsByCode`), `IngredientRepository` (`findByLowerNames`), `CategoryRepository` / `SkinTypeRepository` / `SkinConcernRepository` (`findAllByOrderBySortOrderAsc`), V15.
- `ProductRepository` (+ `JpaSpecificationExecutor` cho tìm kiếm admin): `findByIdAndActiveTrue` (store, G-06); danh sách store dùng Specification `ProductService.storeSpec` (active + loại da qua join `product_skin_type` + danh mục + khoảng giá web qua join `product_channel`, T-09.2), `existsBySku`.
- Ràng buộc unique của `sku` tên là `product_sku_key` (Postgres tự đặt ở V1). `ProductService` dựa vào tên này để trả 409 khi 2 request tạo cùng SKU một lúc.
- `CustomerOrderRepository` (T-09.3): CRUD mặc định; T-23.1 thêm `findHistoryIds` (native, đơn WEB + đơn sàn của danh tính đã liên kết) và `findWithItems` (`JOIN FETCH` dòng sản phẩm).
- `CustomerRepository`: `findByEmail`, `findByPhone`.
- `IngredientAliasRepository` (V24): `findByAliasLowerIn` (theo bí danh, `seq`).
- `PromotionRepository` (+ `JpaSpecificationExecutor` cho lọc admin theo kênh, trạng thái): `findVisible` (đang bật, chưa hết hạn, bắt đầu trước mốc), `findOverlappingFlashSales`, `existsByChannelCodeAndVoucherCode[AndIdNot]`. `PriceHistoryRepository`: theo sản phẩm (và kênh), mới nhất trước.
- `OtpChallengeRepository` (T-13.1): `findFirstByEmailOrderByCreatedAtDescIdDesc` (khoá `PESSIMISTIC_WRITE`, chống đoán mã song song), `countByEmailAndCreatedAtAfter`, `findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc`.
- `ChannelIdentityRepository`: `findByChannelAndExternalId`, `findByCustomerId`.
- `ChannelCredentialRepository`: `findByChannelAndShopId`, `findByChannelOrderByShopId`.
- `ConversationRepository`: `upsert` (native `INSERT ... ON CONFLICT ... RETURNING id`), `touch` (tăng `message_count`, dời `last_message_at`), `findByChannel`.
- `ConversationMessageRepository`: `existsByConversationIdAndDirectionAndMessageId`, `findByConversationId` (phân trang).

## Dự kiến (chưa có)
Consent log (US-30, S7). Cho trụ E (decisions #23): trạng thái đồng bộ theo kênh (US-49, S3), đơn từ sàn (US-51, S5), trạng thái duyệt sản phẩm (US-54, S7), đánh giá sản phẩm (US-55, S8).

## Database simulator Shopee

Cùng Postgres với hub khi chạy Supabase (database `postgres`). Local/test vẫn có thể dùng database `shopee`. Chỉ simulator `simulators/shopee` dùng (hub không kết nối, decisions #44). Migration nằm ở `simulators/shopee/src/main/resources/db/shopee`, lịch sử riêng `tbl_shopee_flyway_history`. Bảng: `tbl_shopee_item`, `tbl_shopee_price_list`, `tbl_shopee_seller_stock`, `tbl_shopee_order`, `tbl_shopee_order_item`, `tbl_shopee_push`, `tbl_shopee_conversation`, `tbl_shopee_message`, `tbl_shopee_webchat_push`. Giá, tồn, đơn theo field Open Platform đã đọc. Chat là bản giả lập vì trang seller chat bị khóa quyền. Chi tiết: [../channel-api/shopee.md](../channel-api/shopee.md).

## Test
`OmniAiApplicationTests` chạy Flyway trên Postgres thật (Testcontainers) và kiểm tra: số SKU / active / hết hàng / hãng đọc từ CSV (`TestCatalog`), V9 đã xoá SKU hư cấu, extension vector, ràng buộc V4 (`usage_time` sai), và V13: không còn cột `price_*` trong `product`, `product_channel` có đúng giá CSV cho mọi SKU × 3 kênh, giá âm / kênh lạ / trùng kênh bị DB từ chối, view chatbot vẫn có `price_web/shopee/tiktok`, thêm kênh `LAZADA` chỉ bằng INSERT. Vì vậy cần Docker. `channel/tiktok/TiktokWebhookIT` lưu và đọc lại `channel_credential` (V8). `conversation/ConversationStoreIT` kiểm V11 qua `MessageRouter` thật: IN/OUT, vi phạm, chống trùng, mỗi kênh 1 hội thoại. `catalog/CatalogNormalizationTest` kiểm V15: không còn 8 cột cũ; bảng tra cứu đúng; **từng SKU** có hãng, tập loại da, tập vấn đề da, buổi dùng, INCI đúng thứ tự (bỏ trùng), ảnh chính khớp CSV; số SKU theo nhóm hoạt chất khớp CSV; view chatbot giữ cột và giá trị; DB chặn loại da lạ, thành phần trùng, 2 ảnh chính, nhóm hoạt chất lạ. `promotion/PriceHistoryTest` kiểm mốc đầu V23 (mọi dòng `product_channel` có 1 dòng lịch sử). `simulators/shopee` có test riêng: `OpenApiTest` kiểm giá, tồn, đơn và chat ghi đúng các bảng `tbl_shopee_*`, `UiTest` kiểm các API `/sim/ui/*`.
