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

import java.time.Instant;

/** 1 lần danh tính kênh gõ mã liên kết thất bại (V21). Đếm theo cửa sổ 1 giờ để khoá. */
@Entity
@Table(name = "link_code_attempt")
@Getter
@NoArgsConstructor
public class LinkCodeAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    LinkCodeAttempt(ChannelType channel, String externalId, Instant attemptedAt) {
        this.channel = channel;
        this.externalId = externalId;
        this.attemptedAt = attemptedAt;
    }
}
