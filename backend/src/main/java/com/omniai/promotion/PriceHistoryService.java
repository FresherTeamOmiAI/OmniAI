package com.omniai.promotion;

import com.omniai.catalog.ProductErrors.FieldViolation;
import com.omniai.catalog.ProductErrors.NotFound;
import com.omniai.catalog.ProductRepository;
import com.omniai.catalog.SalesChannelRepository;
import com.omniai.promotion.PromotionErrors.Invalid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/** Đọc lịch sử giá niêm yết của 1 sản phẩm (US-33). Ghi ở {@link PriceHistoryRecorder}. */
@Service
@Transactional(readOnly = true)
public class PriceHistoryService {

    /** Một lần đổi giá. {@code oldPrice} null = giá đầu tiên (mốc V23 hoặc lúc tạo sản phẩm). */
    public record Entry(String channel, Long oldPrice, long newPrice, OffsetDateTime changedAt) {
    }

    private final PriceHistoryRepository history;
    private final ProductRepository products;
    private final SalesChannelRepository channels;

    public PriceHistoryService(PriceHistoryRepository history, ProductRepository products,
                               SalesChannelRepository channels) {
        this.history = history;
        this.products = products;
        this.channels = channels;
    }

    /** Mới nhất trước. {@code channel} null = mọi kênh. Sản phẩm không có thì 404, kênh lạ thì 400. */
    public Page<Entry> history(Long productId, String channel, Pageable pageable) {
        if (!products.existsById(productId)) {
            throw new NotFound(productId);
        }
        Page<PriceHistory> page;
        if (channel == null || channel.isBlank()) {
            page = history.findByProductIdOrderByChangedAtDescIdDesc(productId, pageable);
        } else {
            String code = channel.strip().toUpperCase(Locale.ROOT);
            if (!channels.existsById(code)) {
                throw new Invalid(List.of(new FieldViolation("channel", "kênh không tồn tại")));
            }
            page = history.findByProductIdAndChannelCodeOrderByChangedAtDescIdDesc(productId, code, pageable);
        }
        return page.map(h -> new Entry(h.getChannelCode(), h.getOldPrice(), h.getNewPrice(),
                PromotionService.local(h.getChangedAt())));
    }
}
