package com.omniai.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Một lần đổi giá niêm yết của 1 sản phẩm ở 1 kênh (V23, US-33). {@code oldPrice} null = giá đầu tiên. */
@Entity
@Table(name = "price_history")
@Getter
@NoArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "channel_code", nullable = false, length = 20)
    private String channelCode;

    @Column(name = "old_price")
    private Long oldPrice;

    @Column(name = "new_price", nullable = false)
    private long newPrice;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    PriceHistory(Long productId, String channelCode, Long oldPrice, long newPrice, Instant changedAt) {
        this.productId = productId;
        this.channelCode = channelCode;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.changedAt = changedAt;
    }
}
