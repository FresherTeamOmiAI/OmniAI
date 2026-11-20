package com.omniai.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {

    /** Khuyến mãi đang bật của kênh, chưa hết hạn lúc {@code now} và bắt đầu không muộn hơn {@code startsBefore}. */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.channelCode = :channel AND p.enabled = true AND p.endsAt > :now AND p.startsAt <= :startsBefore
            ORDER BY p.startsAt, p.id""")
    List<Promotion> findVisible(@Param("channel") String channel, @Param("now") Instant now,
                                @Param("startsBefore") Instant startsBefore);

    /** Flash sale đang bật của kênh có thời gian giao với [from, to): chặn 2 giá sale cùng lúc cho 1 sản phẩm. */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.channelCode = :channel AND p.type = com.omniai.promotion.Promotion.Type.FLASH_SALE
              AND p.enabled = true AND p.startsAt < :to AND p.endsAt > :from""")
    List<Promotion> findOverlappingFlashSales(@Param("channel") String channel, @Param("from") Instant from,
                                              @Param("to") Instant to);

    boolean existsByChannelCodeAndVoucherCode(String channelCode, String voucherCode);

    boolean existsByChannelCodeAndVoucherCodeAndIdNot(String channelCode, String voucherCode, Long id);
}
