package com.omniai.channel.tiktok;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Cấu hình đẩy sản phẩm ra TikTok Shop (`omniai.tiktok.products.*`, US-50).
 *
 * @param enabled        tắt thì không có connector TIKTOK trong khung đồng bộ
 * @param warehouseId    kho của shop (thật: lấy từ Get Warehouse List; simulator: sim.products.warehouse-id)
 * @param categories     danh mục hub (category.code) → category_id lá trên TikTok. Thật: lấy từ Get Categories;
 *                       mặc định {@link #DEFAULT_CATEGORIES} khớp simulator (giả định, chưa đối chiếu cây danh mục VN).
 *                       Khoá có dấu gạch trong YAML viết dạng {@code "[spot-treatment]": 601456}
 * @param packagingGrams khối lượng vỏ hộp cộng vào dung tích (1 ml ≈ 1 g) để ra package_weight
 */
@ConfigurationProperties(prefix = "omniai.tiktok.products")
public record TiktokProductProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("7494049642642500001") String warehouseId,
        Map<String, String> categories,
        @DefaultValue("50") int packagingGrams) {

    public static final Map<String, String> DEFAULT_CATEGORIES = Map.of(
            "cleanser", "601450", "toner", "601451", "serum", "601452", "moisturizer", "601453",
            "sunscreen", "601454", "exfoliant", "601455", "spot-treatment", "601456", "mask", "601457",
            "eye-cream", "601458", "lip-care", "601459");

    public TiktokProductProperties {
        if (categories == null || categories.isEmpty()) {
            categories = DEFAULT_CATEGORIES;
        }
    }
}
