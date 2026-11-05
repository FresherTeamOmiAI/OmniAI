# Mô hình dữ liệu

> Cập nhật: 2026-11-05 · commit af8e70c · Owner: LucVT (catalog, customer, identity, order, promotion)
> Nguồn: `backend/src/main/resources/db/migration/*.sql`, `backend/.../catalog/*`, `backend/.../customer/*`

DB: PostgreSQL 16 + pgvector (image `pgvector/pgvector:pg16`). Schema do **Flyway** quản lý, JPA để `ddl-auto: validate`. Vì vậy mọi thay đổi bảng đều phải viết migration mới `V<n>__<mo_ta>.sql`. **Không sửa migration đã merge.**

## Migration

| File | Nội dung |
|---|---|
| `V1__init.sql` | Bảng `product`, `customer`, `channel_identity` |
| `V2__seed_products.sql` | 8 SKU mẫu (Lumi Lab, Sen Việt, Hydra Mint, đều hư cấu). Sẽ thay bằng 80 SKU (T-04.2, T-08.2) |
| `V3__enable_pgvector.sql` | `CREATE EXTENSION vector`. Cột embedding sẽ thêm ở S2 (US-10) |
| `V4__catalog_schema_v1.sql` | Schema catalog đợt 1 (T-04.1): thêm `description`, `skin_concerns`, `usage_time`, `updated_at`; NOT NULL cho các trường AC US-04 bắt buộc; CHECK giá/tồn kho ≥ 0, dung tích/PAO > 0; index `category` |

## Bảng

**product** (entity `catalog/Product.java`). Bắt buộc (NOT NULL) = mọi cột trừ `description`, `skin_concerns`, `usage_time`.
- `sku` unique, dạng `<BRAND>-<LOAI>-<NN>` (vd. `LUMI-SUN-01`). `name`, `brand` (thương hiệu **hư cấu**).
- `category`: `cleanser, toner, serum, moisturizer, sunscreen, exfoliant, spot-treatment, mask, eye-cream, lip-care`.
- `skin_types`: CSV các giá trị `oily,dry,combination,sensitive,normal`.
- `skin_concerns`: CSV vấn đề da, dùng để lọc/RAG: `acne, oil-control, pores, hydration, dullness, dark-spots, aging, sensitivity, barrier, sun-protection`.
- `usage_time`: `am | pm | am,pm` (CHECK trong DB). Dùng cho routine sáng/tối (US-31).
- `description`: mô tả tiếng Việt ≤ 2000 ký tự (công dụng, kết cấu, cách dùng), cho trang chi tiết và embedding RAG. **Không** ghi giá hay claim y khoa ("trị", "chữa").
- `ingredients_inci`: CSV INCI, tối đa 4000 ký tự. Luật thành phần (`ai-brain/app/rules/ingredients.py`) đọc từ đây.
- `size_ml` > 0, `pao_months` > 0 (dùng được bao lâu sau khi mở).
- Giá theo kênh, đơn vị VND (BIGINT, ≥ 0): `price_web`, `price_shopee`, `price_tiktok`.
- `stock` ≥ 0, `active` (false = ẩn khỏi store và AI không được gợi ý). `updated_at` tự cập nhật khi sửa qua JPA (`@PreUpdate`).

**customer** (`customer/Customer.java`): khách hợp nhất. `display_name`, `email` unique, `phone` unique, `created_at`.

**channel_identity** (`customer/ChannelIdentity.java`): một danh tính trên một kênh.
- `customer_id` FK, có thể null khi chưa liên kết. `channel` (enum `ChannelType`), `external_id`. Unique (`channel`, `external_id`).
- `linked_by`: `OTP | DEEP_LINK | LINK_CODE | ORDER_CLAIM | PROBABILISTIC_CONFIRMED`.
- `confidence`: 1.0 nếu liên kết tất định, nhỏ hơn 1 nếu đến từ gợi ý xác suất đã được khách xác nhận.
- `consent_at`.
- Nguyên tắc: chỉ gắn vào customer khi có bằng chứng + consent (Luật BVDLCN 91/2025). **Không bao giờ tự gộp.**

## Repository
- `ProductRepository`: `findByActiveTrue`, `findByActiveTrueAndSkinTypesContainingIgnoreCase`.
- `CustomerRepository`: `findByEmail`, `findByPhone`.
- `ChannelIdentityRepository`: `findByChannelAndExternalId`, `findByCustomerId`.

## Dự kiến (chưa có)
`conversation`/`message` (US-11, S2), cột vector cho product (US-10, S2), OTP/JWT (US-13, S3), mã liên kết OMNI-xxxx (US-22, S5), order (US-09/23), promotion/voucher/lịch sử giá (US-33, S8), consent log (US-30, S7).

## Test
`OmniAiApplicationTests` chạy Flyway trên Postgres thật (Testcontainers) và kiểm tra có seed sản phẩm + extension vector, cùng các ràng buộc V4 (giá âm, `usage_time` sai, thiếu giá kênh đều bị DB từ chối). Vì vậy cần Docker.
