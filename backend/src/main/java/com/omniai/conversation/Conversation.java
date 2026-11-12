package com.omniai.conversation;

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

import java.time.Instant;

/**
 * Một hội thoại trên một kênh (bảng conversation, V11). Khóa tự nhiên: (channel, externalId),
 * với externalId = {@code CanonicalMessage.conversationId}. Owner: TuTM.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "channel_user_id", nullable = false)
    private String channelUserId;

    /** Null khi danh tính kênh chưa liên kết với khách (US-13, US-22). */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    /** Số dòng tin (IN + OUT). */
    @Column(name = "message_count", nullable = false)
    private int messageCount;
}
