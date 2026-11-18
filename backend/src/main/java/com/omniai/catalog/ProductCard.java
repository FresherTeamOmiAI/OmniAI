package com.omniai.catalog;

import java.util.Map;

/**
 * Một thẻ sản phẩm trong danh sách store {@code GET /api/products} (US-43, T-43.1). Chỉ các trường thẻ và chat widget
 * cần, bỏ INCI, mô tả, nguồn, giấy phép ảnh, barcode (~1,5 KB/SP xuống còn vài trăm byte). Chi tiết đầy đủ:
 * {@code GET /api/products/{id}}.
 */
public record ProductCard(Long id, String sku, String brand, String name, String category, String skinTypes,
                          String imageUrl, Map<String, Long> prices, int stock) {

    static ProductCard of(Product p) {
        return new ProductCard(p.getId(), p.getSku(), p.getBrand(), p.getName(), p.getCategory(), p.getSkinTypes(),
                p.getImageUrl(), p.getPrices(), p.getStock());
    }
}
