package com.omniai.conversation;

import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.conversation.ConversationMessage.DeliveryStatus;
import com.omniai.conversation.ConversationMessage.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lưu hội thoại mọi kênh (US-11). Mỗi lần ghi là 1 transaction ngắn riêng, không ôm transaction trong lúc
 * chờ AI Brain. Tin trùng (cùng hội thoại, chiều, messageId; vd. gửi lại sau khi restart mất cache idempotency)
 * thì bỏ qua. Owner: TuTM.
 */
@Service
public class ConversationService implements ConversationRecorder {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final Sort LIST_SORT = Sort.by(Sort.Order.desc("lastMessageAt"), Sort.Order.desc("id"));
    private static final Sort MESSAGE_SORT = Sort.by("createdAt", "id");

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

    @Override
    @Transactional
    public void delivery(CanonicalMessage message, DeliveryStatus status, int attempts, String error, String sentText) {
        Optional<ConversationMessage> found = conversations
                .findByChannelAndExternalId(message.channel(), externalIdOf(message))
                .flatMap(c -> messages.findByConversationIdAndDirectionAndMessageId(c.getId(), Direction.OUT,
                        message.messageId()));
        if (found.isEmpty()) {
            // Lưu câu trả lời đã lỗi trước đó (MessageRouter chỉ log), không có gì để cập nhật.
            log.warn("Không có câu trả lời đã lưu cho {}:{} để ghi trạng thái gửi {}", message.channel(),
                    message.messageId(), status);
            return;
        }
        ConversationMessage row = found.get();
        row.setDeliveryStatus(status);
        row.setDeliveryAttempts(attempts);
        row.setDeliveryError(error == null ? null : truncate(error));
        if (status == DeliveryStatus.FAILED) {
            row.setHandoff(true);
        }
        if (sentText != null) {
            row.setText(sentText);
        }
    }

    /** Danh sách hội thoại theo bộ lọc (T-14.2), mới nhất trước, kèm số câu bị can thiệp / chuyển nhân viên. */
    @Transactional(readOnly = true)
    public Page<ConversationView> list(ConversationFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, LIST_SORT);
        Page<Conversation> result = conversations.findAll(filter.toSpecification(), pageable);
        Map<Long, ConversationRepository.Stats> stats = result.isEmpty() ? Map.of()
                : conversations.statsOf(result.map(Conversation::getId).getContent()).stream()
                .collect(Collectors.toMap(ConversationRepository.Stats::getConversationId, Function.identity()));
        return result.map(c -> ConversationView.of(c, stats.get(c.getId())));
    }

    /** Các tin của 1 hội thoại theo thứ tự thời gian; rỗng nếu không có hội thoại đó. */
    @Transactional(readOnly = true)
    public Optional<Page<MessageView>> messages(Long conversationId, int page, int size) {
        if (!conversations.existsById(conversationId)) {
            return Optional.empty();
        }
        return Optional.of(messages.findByConversationId(conversationId, PageRequest.of(page, size, MESSAGE_SORT))
                .map(MessageView::of));
    }

    private static String externalIdOf(CanonicalMessage message) {
        return message.conversationId() != null
                ? message.conversationId()
                : message.channel().name().toLowerCase() + ":" + message.channelUserId();
    }

    private static String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    private Long conversationIdOf(CanonicalMessage message, Instant at) {
        String externalId = externalIdOf(message);
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
