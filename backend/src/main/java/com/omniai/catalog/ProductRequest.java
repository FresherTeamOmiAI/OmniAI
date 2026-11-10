package com.omniai.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Body của POST/PUT /api/admin/products. Bean Validation kiểm có mặt, độ dài, khoảng số;
 * bộ từ vựng, dạng SKU, claim y khoa do ProductService kiểm sau khi chuẩn hóa.
 *
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
        @NotNull(message = "bắt buộc") @PositiveOrZero(message = "phải >= 0") Long priceWeb,
        @NotNull(message = "bắt buộc") @PositiveOrZero(message = "phải >= 0") Long priceShopee,
        @NotNull(message = "bắt buộc") @PositiveOrZero(message = "phải >= 0") Long priceTiktok,
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
                sizeMl, paoMonths, priceWeb, priceShopee, priceTiktok, stock, active);
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

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
