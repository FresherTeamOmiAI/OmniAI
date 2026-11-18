package com.omniai.sync;

import java.time.Instant;

/**
 * Trạng thái đồng bộ 1 sản phẩm trên 1 kênh, cho admin (T-49.3). {@code lastError} có khi lần gửi gần nhất lỗi;
 * {@code nextAttemptAt} là lúc thử lại (PENDING).
 */
public record SyncView(
        Long productId,
        String channel,
        SyncAspect aspect,
        SyncStatus status,
        int attempts,
        String lastError,
        String externalId,
        Instant syncedAt,
        Instant nextAttemptAt,
        Instant updatedAt) {

    static SyncView of(ProductSyncState s) {
        return new SyncView(s.getId().getProductId(), s.getId().getChannel(), s.getId().getAspect(), s.getStatus(), s.getAttempts(),
                s.getLastError(), s.getExternalId(), s.getSyncedAt(), s.getNextAttemptAt(), s.getUpdatedAt());
    }
}
