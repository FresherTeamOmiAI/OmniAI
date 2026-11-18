package com.omniai.identity;

import com.omniai.common.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

/** 1 lần khách lấy mã liên kết OMNI (V21). Chỉ lưu hash của mã. */
@Entity
@Table(name = "link_code")
@Getter
@Setter
@NoArgsConstructor
public class LinkCode {

    public enum Resolution { CONFIRMED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_channel", length = 20)
    private ChannelType claimedChannel;

    @Column(name = "claimed_external_id")
    private String claimedExternalId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Resolution resolution;

    /** Còn gõ vào chat được: chưa ai nhận, chưa bị thu hồi, chưa hết 15 phút. */
    boolean isClaimableAt(Instant now) {
        return claimedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    Instant confirmBefore(Duration window) {
        return claimedAt == null ? null : claimedAt.plus(window);
    }

    /** Đã có danh tính sàn gõ mã, khách chưa xác nhận hay từ chối, còn trong hạn xác nhận. */
    boolean isPendingAt(Instant now, Duration window) {
        return claimedAt != null && resolution == null && confirmBefore(window).isAfter(now);
    }
}
