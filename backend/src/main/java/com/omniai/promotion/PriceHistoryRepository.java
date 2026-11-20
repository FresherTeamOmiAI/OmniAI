package com.omniai.promotion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    Page<PriceHistory> findByProductIdOrderByChangedAtDescIdDesc(Long productId, Pageable pageable);

    Page<PriceHistory> findByProductIdAndChannelCodeOrderByChangedAtDescIdDesc(Long productId, String channelCode,
                                                                              Pageable pageable);
}
