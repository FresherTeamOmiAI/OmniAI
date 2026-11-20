package com.omniai.promotion;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Khuyến mãi trả ra API (admin và công khai). Mốc giờ theo Asia/Ho_Chi_Minh ({@code +07:00}).
 * {@code status} tính lúc đọc: SCHEDULED, ACTIVE, EXPIRED, DISABLED.
 */
public record PromotionView(Long id, String channel, Promotion.Type type, String name, String voucherCode,
                            Long discountAmount, Long minOrderAmount, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            Promotion.Status status, boolean enabled, List<Item> items) {

    /**
     * Một dòng flash sale. {@code listPrice}: giá niêm yết hiện tại ở kênh của khuyến mãi (null nếu sản phẩm chưa có
     * giá ở kênh đó). {@code discountPercent}: % giảm so với {@code listPrice}, làm tròn xuống (UC-17 so ngưỡng 10%).
     */
    public record Item(Long productId, String sku, String name, Long listPrice, long salePrice,
                       Integer discountPercent) {
    }
}
