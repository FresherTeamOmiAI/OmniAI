package com.omniai.conversation;

import com.omniai.conversation.ConversationMessage.Direction;
import com.omniai.policy.PolicyViolation;

import java.time.Instant;
import java.util.List;

/**
 * 1 tin của GET /api/conversations/{id}/messages. IN: {@code text} là tin khách, {@code rawText} null.
 * OUT: {@code text} là finalText khách nhận, {@code rawText} là câu gốc của AI Brain.
 */
public record MessageView(
        Long id,
        Direction direction,
        String messageId,
        String text,
        String rawText,
        List<PolicyViolation> violations,
        boolean blocked,
        boolean handoff,
        Instant createdAt) {

    static MessageView of(ConversationMessage m) {
        return new MessageView(m.getId(), m.getDirection(), m.getMessageId(), m.getText(), m.getRawText(),
                m.getViolations().stream().map(StoredViolation::toViolation).toList(),
                m.isBlocked(), m.isHandoff(), m.getCreatedAt());
    }
}
