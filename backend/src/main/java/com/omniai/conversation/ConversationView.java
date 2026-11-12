package com.omniai.conversation;

import com.omniai.common.ChannelType;

import java.time.Instant;

/** 1 dòng trong danh sách hội thoại của GET /api/conversations. {@code conversationId} = id hội thoại phía kênh. */
public record ConversationView(
        Long id,
        ChannelType channel,
        String conversationId,
        String channelUserId,
        Long customerId,
        Instant startedAt,
        Instant lastMessageAt,
        int messageCount) {

    static ConversationView of(Conversation c) {
        return new ConversationView(c.getId(), c.getChannel(), c.getExternalId(), c.getChannelUserId(),
                c.getCustomerId(), c.getStartedAt(), c.getLastMessageAt(), c.getMessageCount());
    }
}
