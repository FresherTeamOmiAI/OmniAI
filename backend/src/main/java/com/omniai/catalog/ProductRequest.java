package com.omniai.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Body của POST/PUT /api/admin/products. Bean Validation kiểm có mặt, độ dài, khoảng số;
 * bộ từ vựng, dạng SKU, claim y khoa do ProductService kiểm sau khi chuẩn hóa.
 *
 * @param prices giá niêm yết theo kênh (VND), khoá = mã kênh trong sales_channel, vd. {"WEB": 350000, "SHOPEE": 364000}.
 *               Phải có đủ mọi kênh đang active (ProductService kiểm). Không còn 3 field priceWeb/Shopee/Tiktok (decisions #32).
 * @param active bỏ trống khi tạo = true; khi sửa = giữ nguyên.
 */
public record ProductRequest(
        @NotBlank(message = "bắt buộc") String sku,
        @NotBlank(message = "bắt buộc") @Size(max = 255, message = "tối đa 255 ký tự") String name,
        @NotBlank(message = "bắt buộc") @Size(max = 255, message = "tối đa 255 ký tự") String brand,
        @NotBlank(message = "bắt buộc") String category,
        @NotBlank(message = "bắt buộc") String skinTypes,
        String skinConcerns,
        String usageTime,
        @Size(max = 2000, message = "tối đa 2000 ký tự") String description,
        @NotBlank(message = "bắt buộc") @Size(max = 4000, message = "tối đa 4000 ký tự") String ingredientsInci,
        @NotNull(message = "bắt buộc") @Positive(message = "phải > 0") Integer sizeMl,
        @NotNull(message = "bắt buộc") @Positive(message = "phải > 0") Integer paoMonths,
        @NotNull(message = "bắt buộc")
        Map<String, @NotNull(message = "bắt buộc") @PositiveOrZero(message = "phải >= 0") Long> prices,
        @NotNull(message = "bắt buộc") @PositiveOrZero(message = "phải >= 0") Integer stock,
        Boolean active) {

    /** Trim, chuẩn Unicode NFC, viết hoa SKU, viết thường + bỏ trùng các trường CSV, chuỗi rỗng thành null. */
    ProductRequest normalized() {
        return new ProductRequest(
                text(sku).toUpperCase(Locale.ROOT),
                text(name),
                text(brand),
                text(category).toLowerCase(Locale.ROOT),
                csv(skinTypes, true, ","),
                blankToNull(csv(skinConcerns, true, ",")),
                blankToNull(csv(usageTime, true, ",")),
                blankToNull(text(description)),
                csv(ingredientsInci, false, ", "),
                sizeMl, paoMonths, channelPrices(prices), stock, active);
    }

    private static String text(String value) {
        return value == null ? "" : Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    private static String csv(String value, boolean lowerCase, String separator) {
        List<String> parts = CatalogVocabulary.split(text(value));
        return String.join(separator, new LinkedHashSet<>(lowerCase
                ? parts.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList()
                : parts));
    }

    /** Mã kênh viết hoa, bỏ khoảng trắng; giữ thứ tự gửi lên. */
    private static Map<String, Long> channelPrices(Map<String, Long> prices) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (prices != null) {
            prices.forEach((channel, price) -> result.put(text(channel).toUpperCase(Locale.ROOT), price));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
