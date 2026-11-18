package com.omniai.conversation;

import com.omniai.brain.BrainTurn;
import com.omniai.chat.HistoryWindow;
import com.omniai.common.CanonicalMessage;
import com.omniai.conversation.ConversationMessage.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Đọc lịch sử từ bảng conversation của T-11 để đưa vào AI Brain. Không ghi thêm bảng riêng. */
@Service
public class ConversationHistory implements TurnHistory {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    public ConversationHistory(ConversationRepository conversations, ConversationMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrainTurn> before(CanonicalMessage message) {
        Conversation conversation = conversations
                .findByChannelAndExternalId(message.channel(), message.conversationId())
                .orElse(null);
        if (conversation == null || !message.channelUserId().equals(conversation.getChannelUserId())) {
            return List.of();
        }
        List<ConversationMessage> recent = messages
                .findTop20ByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId());
        Collections.reverse(recent);
        List<BrainTurn> turns = new ArrayList<>();
        for (ConversationMessage row : recent) {
            if (row.getDirection() == Direction.IN && message.messageId().equals(row.getMessageId())) {
                continue;
            }
            String role = row.getDirection() == Direction.IN ? "user" : "assistant";
            turns.add(new BrainTurn(role, row.getText()));
        }
        return HistoryWindow.select(turns);
    }
}
