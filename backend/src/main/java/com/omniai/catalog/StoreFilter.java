package com.omniai.catalog;

import java.util.Locale;

/**
 * Bộ lọc của web store, GET /api/products (T-09.2, US-09). Owner: LucVT.
 * Mọi tham số tuỳ chọn, kết hợp bằng AND. Giá lọc theo giá niêm yết của kênh web (product_channel, V13),
 * vì store là kênh WEB của shop (SRS G-15).
 *
 * @param skinType 1 loại da trong {@link CatalogVocabulary#SKIN_TYPES}; khớp nguyên giá trị trong cột CSV skin_types.
 * @param category 1 danh mục trong {@link CatalogVocabulary#CATEGORIES}.
 * @param minPrice giá web tối thiểu (VND, gồm cả mốc).
 * @param maxPrice giá web tối đa (VND, gồm cả mốc).
 */
public record StoreFilter(String skinType, String category, Long minPrice, Long maxPrice) {

    /** Kênh có giá dùng cho store. */
    public static final String STORE_CHANNEL = "WEB";

    /** Trim, viết thường, chuỗi rỗng thành null (query string `?skinType=` coi như không lọc). */
    StoreFilter normalized() {
        return new StoreFilter(word(skinType), word(category), minPrice, maxPrice);
    }

    private static String word(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
