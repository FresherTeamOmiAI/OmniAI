package com.omniai.sync;

/**
 * Gửi sản phẩm từ hub ra 1 kênh bán (US-49). Mỗi sàn 1 bean; khung đồng bộ chỉ gửi ra kênh có connector và
 * {@code sales_channel.active = true}. Connector Shopee viết sau khi simulator nhận sản phẩm xong (T-49.2),
 * TikTok Shop ở US-50.
 * <p>
 * Ném {@link SyncException} với {@code retryable=false} khi thử lại cũng vô ích (dữ liệu bị sàn từ chối, chưa ủy
 * quyền...). Mọi exception khác (mạng, 5xx, rate limit) được coi là lỗi tạm thời và thử lại có backoff.
 */
public interface ProductChannelConnector {

    /** Mã kênh, trùng {@code sales_channel.code} (vd. SHOPEE). */
    String channel();

    /**
     * Tạo sản phẩm trên sàn khi {@code externalId == null}, ngược lại sửa sản phẩm đó (gồm ẩn/hiện theo
     * {@link ProductSnapshot#active()}). Trả mã sản phẩm phía sàn.
     */
    String push(ProductSnapshot product, String externalId);
}
