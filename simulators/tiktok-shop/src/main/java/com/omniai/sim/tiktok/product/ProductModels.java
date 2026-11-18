package com.omniai.sim.tiktok.product;

import java.util.List;

/**
 * DTO nhóm Products theo docv2 (lưu 11/11/2026, docs/channel-api/tiktok-dto.md mục Products). Tên trường camelCase,
 * Jackson đổi sang snake_case. Chỉ giữ các trường simulator dùng; trường khác của sàn (certifications, video,
 * size_chart, product_attributes...) được bỏ qua khi đọc.
 */
public final class ProductModels {

    private ProductModels() {
    }

    public record Image(String uri) {
    }

    /** {@code amount} là chuỗi số (VND không có phần lẻ), {@code currency} bắt buộc. */
    public record Price(String amount, String currency) {
    }

    public record Inventory(String warehouseId, Integer quantity) {
    }

    public record Sku(String id, String sellerSku, Price price, List<Inventory> inventory) {
    }

    public record Weight(String value, String unit) {
    }

    /** Body của Create Product (202309) và Edit Product (202509). */
    public record ProductRequest(String title, String description, String categoryId, String brandId,
                                 List<Image> mainImages, List<Sku> skus, Weight packageWeight, Boolean isCodAllowed,
                                 String saveMode, String idempotencyKey) {
    }

    public record SkuRef(String id, String sellerSku) {
    }

    /** data của Create/Edit Product: product_id, skus[].id, skus[].seller_sku, warnings. */
    public record ProductResponse(String productId, List<SkuRef> skus, List<Object> warnings) {
    }

    /** Body của Update Price và Update Inventory: skus[].id + price / inventory. */
    public record SkuUpdate(List<Sku> skus) {
    }

    /** Body của Activate / Deactivate Products. */
    public record ProductIds(List<String> productIds) {
    }

    /** data của Upload Product Image. */
    public record UploadedImage(String uri, String url, int height, int width, String useCase) {
    }

    // ---------- trạng thái simulator giữ ----------

    public record StoredSku(String id, String sellerSku, String priceAmount, String currency, String warehouseId,
                            int quantity) {
    }

    /** Trạng thái sản phẩm theo docv2: ACTIVATE, SELLER_DEACTIVATED (chưa giả lập PENDING/FAILED duyệt, US-54). */
    public record StoredProduct(String id, String title, String description, String categoryId, String brandId,
                                List<String> mainImages, List<StoredSku> skus, Weight packageWeight, String status,
                                long createTime, long updateTime) {
    }
}
