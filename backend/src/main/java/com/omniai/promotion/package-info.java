/**
 * Khuyến mãi theo kênh (voucher, flash sale) và lịch sử giá niêm yết (Owner: LucVT, US-33 S5, decisions #53).
 * AI Brain chỉ được nói con số lấy từ module này, không tự bịa giá/giờ sale (C-07).
 * <ul>
 *   <li>{@link com.omniai.promotion.PromotionService}: admin tạo/sửa/tắt/xoá; đọc công khai theo kênh.</li>
 *   <li>{@link com.omniai.promotion.PriceHistoryRecorder}: nghe {@code catalog.ProductPricesChanged}, ghi lịch sử giá.</li>
 *   <li>{@link com.omniai.promotion.PromotionChanged}: sự kiện cho đồng bộ khuyến mãi ra sàn (US-52).</li>
 * </ul>
 */
package com.omniai.promotion;
