package com.omniai.promotion;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Khuyến mãi của 1 kênh (V23, US-33, decisions #53). VOUCHER: mã + giảm số tiền cố định cho đơn từ
 * {@code minOrderAmount}, áp cả shop trên kênh. FLASH_SALE: giá sale riêng từng sản phẩm ({@link #items}).
 * Trạng thái không lưu, tính bằng {@link #status(Instant)}.
 */
@Entity
@Table(name = "promotion")
@Getter
@Setter
@NoArgsConstructor
public class Promotion {

    public enum Type { VOUCHER, FLASH_SALE }

    public enum Status { SCHEDULED, ACTIVE, EXPIRED, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_code", nullable = false, length = 20)
    private String channelCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "voucher_code", length = 20)
    private String voucherCode;

    @Column(name = "discount_amount")
    private Long discountAmount;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Flash sale: product_id → giá sale (VND) ở kênh {@link #channelCode}. Voucher: rỗng. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_item", joinColumns = @JoinColumn(name = "promotion_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "sale_price", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 50)
    private Map<Long, Long> items = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Tắt tay thắng mọi mốc giờ; còn lại theo {@code now} so với [startsAt, endsAt). */
    public Status status(Instant now) {
        if (!enabled) {
            return Status.DISABLED;
        }
        if (now.isBefore(startsAt)) {
            return Status.SCHEDULED;
        }
        return now.isBefore(endsAt) ? Status.ACTIVE : Status.EXPIRED;
    }
}
