package com.omniai.conversation;

import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.conversation.ConversationMessage.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lưu hội thoại mọi kênh (US-11). Mỗi lần ghi là 1 transaction ngắn riêng, không ôm transaction trong lúc
 * chờ AI Brain. Tin trùng (cùng hội thoại, chiều, messageId; vd. gửi lại sau khi restart mất cache idempotency)
 * thì bỏ qua. Owner: TuTM.
 */
@Service
public class ConversationService implements ConversationRecorder {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    @Transactional
    public void inbound(CanonicalMessage message) {
        Instant at = message.receivedAt() != null ? message.receivedAt() : Instant.now();
        ConversationMessage row = newRow(message, Direction.IN, at);
        row.setText(message.text() == null ? "" : message.text());
        save(conversationIdOf(message, at), row);
    }

    @Override
    @Transactional
    public void reply(CanonicalMessage message, BotReply reply) {
        Instant at = Instant.now();
        ConversationMessage row = newRow(message, Direction.OUT, at);
        row.setText(reply.finalText() == null ? "" : reply.finalText());
        row.setRawText(reply.rawText());
        row.setBlocked(reply.blocked());
        row.setHandoff(reply.handoff());
        if (reply.violations() != null) {
            reply.violations().forEach(v -> row.getViolations().add(StoredViolation.of(v)));
        }
        save(conversationIdOf(message, at), row);
    }

    private Long conversationIdOf(CanonicalMessage message, Instant at) {
        String externalId = message.conversationId() != null
                ? message.conversationId()
                : message.channel().name().toLowerCase() + ":" + message.channelUserId();
        String userId = message.channelUserId() == null ? "" : message.channelUserId();
        return conversations.upsert(message.channel().name(), externalId, userId, message.customerId(), at);
    }

    private static ConversationMessage newRow(CanonicalMessage message, Direction direction, Instant at) {
        ConversationMessage row = new ConversationMessage();
        row.setDirection(direction);
        row.setMessageId(message.messageId());
        row.setCreatedAt(at);
        return row;
    }

    private void save(Long conversationId, ConversationMessage row) {
        if (messages.existsByConversationIdAndDirectionAndMessageId(conversationId, row.getDirection(), row.getMessageId())) {
            return;
        }
        row.setConversationId(conversationId);
        messages.save(row);
        conversations.touch(conversationId, row.getCreatedAt());
    }
}
