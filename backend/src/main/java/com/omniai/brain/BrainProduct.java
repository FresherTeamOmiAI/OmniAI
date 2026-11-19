package com.omniai.brain;

/**
 * Một thẻ sản phẩm AI Brain gợi ý ({@code SuggestedProduct} của ai-brain, T-43.3). Backend chỉ dùng {@code sku} để tra
 * lại sản phẩm đang bán; các trường còn lại để log/đối chiếu.
 *
 * @param price giá của kênh hiện tại, VND
 */
public record BrainProduct(String sku, String brand, String name, Long price, String category) {
}
