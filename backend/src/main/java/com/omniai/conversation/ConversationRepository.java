package com.omniai.conversation;

import com.omniai.common.ChannelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

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

    Page<Conversation> findByChannel(ChannelType channel, Pageable pageable);
}
