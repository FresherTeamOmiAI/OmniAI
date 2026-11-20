package com.omniai.promotion;

import com.omniai.catalog.ProductPricesChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Ghi lịch sử giá (US-33, UC-21 BR "lưu mỗi lần đổi"). Nghe {@link ProductPricesChanged} đồng bộ, nên dòng lịch sử
 * nằm cùng transaction với lần sửa giá: sửa giá lỗi thì không có dòng lịch sử, và ngược lại.
 */
@Component
class PriceHistoryRecorder {

    private final PriceHistoryRepository history;
    private final Clock clock;

    PriceHistoryRecorder(PriceHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    @EventListener
    void onPricesChanged(ProductPricesChanged event) {
        Instant now = clock.instant();
        history.saveAll(event.changes().stream()
                .map(c -> new PriceHistory(event.productId(), c.channelCode(), c.oldPrice(), c.newPrice(), now))
                .toList());
    }
}
