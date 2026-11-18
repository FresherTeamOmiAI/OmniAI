package com.omniai.conversation;

import com.omniai.common.ChannelType;

import java.time.Instant;

/**
 * 1 dòng trong danh sách hội thoại của GET /api/conversations. {@code conversationId} = id hội thoại phía kênh.
 * {@code flaggedCount}: số câu bot bị policy can thiệp; {@code handoffCount}: số câu chuyển nhân viên;
 * {@code lastText}: tin cuối (T-14.2).
 */
public record ConversationView(
        Long id,
        ChannelType channel,
        String conversationId,
        String channelUserId,
        Long customerId,
        Instant startedAt,
        Instant lastMessageAt,
        int messageCount,
        long flaggedCount,
        long handoffCount,
        String lastText) {

    static ConversationView of(Conversation c, ConversationRepository.Stats stats) {
        return new ConversationView(c.getId(), c.getChannel(), c.getExternalId(), c.getChannelUserId(),
                c.getCustomerId(), c.getStartedAt(), c.getLastMessageAt(), c.getMessageCount(),
                stats == null ? 0 : stats.getFlaggedCount(), stats == null ? 0 : stats.getHandoffCount(),
                stats == null ? null : stats.getLastText());
    }
}
