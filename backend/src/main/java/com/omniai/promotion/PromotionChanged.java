package com.omniai.promotion;

/**
 * Sự kiện: admin vừa tạo, sửa, bật/tắt hoặc xoá 1 khuyến mãi (US-33). Phát trong transaction của
 * {@link PromotionService}. Hiện chưa ai nghe; đồng bộ khuyến mãi ra sàn (US-52) sẽ nghe sự kiện này.
 * Khuyến mãi tự hết hạn thì KHÔNG có sự kiện: bên đồng bộ tự so {@code endsAt}.
 */
public record PromotionChanged(Long promotionId, String channelCode, boolean deleted) {
}
