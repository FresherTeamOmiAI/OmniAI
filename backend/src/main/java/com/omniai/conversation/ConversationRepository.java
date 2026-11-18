package com.omniai.conversation;

import com.omniai.common.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long>, JpaSpecificationExecutor<Conversation> {

    /** Số liệu cho 1 trang danh sách (T-14.2): 1 câu SQL cho cả trang, không N+1. */
    interface Stats {
        Long getConversationId();

        /** Câu bot bị policy can thiệp: có luật vi phạm hoặc bị chặn. */
        long getFlaggedCount();

        long getHandoffCount();

        /** Tin cuối cùng (IN hoặc OUT) để xem nhanh ở inbox. */
        String getLastText();
    }

    @Query(value = """
            SELECT c.id AS conversationId,
                   (SELECT COUNT(*) FROM conversation_message m
                     WHERE m.conversation_id = c.id AND m.direction = 'OUT'
                       AND (m.blocked OR EXISTS (SELECT 1 FROM message_violation v WHERE v.message_id = m.id))
                   ) AS flaggedCount,
                   (SELECT COUNT(*) FROM conversation_message m
                     WHERE m.conversation_id = c.id AND m.direction = 'OUT' AND m.handoff) AS handoffCount,
                   (SELECT m.text FROM conversation_message m WHERE m.conversation_id = c.id
                     ORDER BY m.created_at DESC, m.id DESC LIMIT 1) AS lastText
              FROM conversation c
             WHERE c.id IN (:ids)
            """, nativeQuery = true)
    List<Stats> statsOf(Collection<Long> ids);

    /**
     * Tạo hội thoại nếu chưa có, trả id. Dùng ON CONFLICT để 2 tin đầu tiên của cùng hội thoại đến đồng thời
     * (vd. webhook gửi lại) không tạo 2 dòng hoặc làm hỏng transaction.
     */
    @Query(value = """
            INSERT INTO conversation (channel, external_id, channel_user_id, customer_id, started_at, last_message_at, message_count)
            VALUES (:channel, :externalId, :channelUserId, :customerId, :at, :at, 0)
            ON CONFLICT (channel, external_id) DO UPDATE
                SET customer_id = COALESCE(EXCLUDED.customer_id, conversation.customer_id)
            RETURNING id
            """, nativeQuery = true)
    Long upsert(String channel, String externalId, String channelUserId, Long customerId, Instant at);

    /** Tăng số tin và dời mốc tin cuối (không lùi khi tin đến trễ). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Conversation c
               SET c.messageCount = c.messageCount + 1,
                   c.lastMessageAt = CASE WHEN c.lastMessageAt < :at THEN :at ELSE c.lastMessageAt END
             WHERE c.id = :id
            """)
    void touch(Long id, Instant at);

    java.util.Optional<Conversation> findByChannelAndExternalId(ChannelType channel, String externalId);
}
