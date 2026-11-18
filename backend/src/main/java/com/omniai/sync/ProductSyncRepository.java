package com.omniai.sync;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProductSyncRepository extends JpaRepository<ProductSyncState, ProductSyncState.Key> {

    interface Claimed {
        Long getProductId();

        String getChannelCode();

        String getAspect();
    }

    /**
     * Nhận tối đa {@code limit} dòng PENDING đến hạn để gửi, dời hạn sang {@code leaseUntil} để worker khác (instance
     * khác) không nhận trùng trong lúc đang gửi. SKIP LOCKED: 2 worker chạy cùng lúc không chờ nhau.
     */
    @Query(value = """
            UPDATE product_channel_sync s SET next_attempt_at = :leaseUntil
             WHERE (s.product_id, s.channel_code, s.aspect) IN (
                   SELECT product_id, channel_code, aspect FROM product_channel_sync
                    WHERE status = 'PENDING' AND next_attempt_at <= :now
                    ORDER BY next_attempt_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
            RETURNING s.product_id AS productId, s.channel_code AS channelCode, s.aspect AS aspect
            """, nativeQuery = true)
    List<Claimed> claimDue(Instant now, Instant leaseUntil, int limit);

    List<ProductSyncState> findByIdProductIdIn(Collection<Long> productIds);

    Page<ProductSyncState> findByStatus(SyncStatus status, Pageable pageable);

    Page<ProductSyncState> findByStatusAndIdChannel(SyncStatus status, String channel, Pageable pageable);

    Page<ProductSyncState> findByIdChannel(String channel, Pageable pageable);
}
