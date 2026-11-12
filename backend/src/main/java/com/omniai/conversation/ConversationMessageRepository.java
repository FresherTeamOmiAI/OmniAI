package com.omniai.conversation;

import com.omniai.conversation.ConversationMessage.Direction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    boolean existsByConversationIdAndDirectionAndMessageId(Long conversationId, Direction direction, String messageId);

    /** Violations nạp lazy trong transaction của service (open-in-view tắt). */
    Page<ConversationMessage> findByConversationId(Long conversationId, Pageable pageable);

    List<ConversationMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    List<ConversationMessage> findTop20ByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);
}
