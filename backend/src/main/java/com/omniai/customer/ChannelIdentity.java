package com.omniai.customer;

import com.omniai.common.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 1 danh tính trên 1 kênh (session web, shopee buyer id, tiktok buyer id...).
 * Chỉ gắn vào Customer khi có bằng chứng + consent (xem LinkMethod).
 */
@Entity
@Table(name = "channel_identity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChannelIdentity {

    public enum LinkMethod { OTP, DEEP_LINK, LINK_CODE, ORDER_CLAIM, PROBABILISTIC_CONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked_by", length = 30)
    private LinkMethod linkedBy;

    /** 1.0 với liên kết tất định; < 1.0 nếu từ gợi ý xác suất đã được khách xác nhận. */
    private Double confidence;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
