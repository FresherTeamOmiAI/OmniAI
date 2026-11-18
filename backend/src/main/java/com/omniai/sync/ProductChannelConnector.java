package com.omniai.sync;

import java.util.Set;

/**
 * Gửi sản phẩm từ hub ra 1 kênh bán (US-49, US-50). Mỗi sàn 1 bean; khung đồng bộ chỉ gửi ra kênh có connector,
 * {@link #ready()} và {@code sales_channel.active = true}.
 * <p>
 * Ném {@link SyncException} với {@code retryable=false} khi thử lại cũng vô ích (dữ liệu bị sàn từ chối, chưa ủy
 * quyền...). Mọi exception khác (mạng, 5xx, rate limit) được coi là lỗi tạm thời và thử lại có backoff.
 */
public interface ProductChannelConnector {

    /** Mã kênh, trùng {@code sales_channel.code} (vd. SHOPEE). */
    String channel();

    /**
     * Phần dữ liệu connector gửi được. Không có PRODUCT thì sản phẩm phải được liên kết tay với mã có sẵn trên sàn
     * (POST /api/admin/sync/products/{id}/{channel}/link) trước khi gửi giá, tồn.
     */
    default Set<SyncAspect> aspects() {
        return Set.of(SyncAspect.PRODUCT);
    }

    /** false = chưa gửi được (vd. shop chưa ủy quyền): không xếp hàng, tránh dồn hàng loạt FAILED. */
    default boolean ready() {
        return true;
    }

    /**
     * Tạo sản phẩm trên sàn khi {@code externalId == null}, ngược lại sửa sản phẩm đó (gồm ẩn/hiện theo
     * {@link ProductSnapshot#active()}). Lần tạo gửi kèm giá và tồn. Trả mã sản phẩm phía sàn.
     */
    default String push(ProductSnapshot product, String externalId) {
        throw new UnsupportedOperationException(channel() + " không gửi nội dung sản phẩm");
    }

    /** Đổi giá niêm yết của kênh này ({@link ProductSnapshot#price()}) cho sản phẩm {@code externalId}. */
    default void pushPrice(ProductSnapshot product, String externalId) {
        throw new UnsupportedOperationException(channel() + " không gửi giá");
    }

    /** Đổi tồn kho ({@link ProductSnapshot#stock()}) cho sản phẩm {@code externalId}. */
    default void pushStock(ProductSnapshot product, String externalId) {
        throw new UnsupportedOperationException(channel() + " không gửi tồn kho");
    }
}
