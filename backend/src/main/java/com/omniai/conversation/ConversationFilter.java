package com.omniai.conversation;

import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationMessage.Direction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lọc của GET /api/conversations (T-14.2). Mọi trường đều tuỳ chọn, kết hợp AND.
 *
 * @param channel      null = mọi kênh
 * @param from         ngày đầu (giờ VN, gồm cả ngày này): hội thoại còn hoạt động từ ngày này
 * @param to           ngày cuối (giờ VN, gồm cả ngày này): hội thoại đã bắt đầu trước hết ngày này
 * @param hasViolation true = có ít nhất 1 câu bot bị policy can thiệp (có luật vi phạm hoặc bị chặn) trong khoảng ngày
 * @param handoff      true = có ít nhất 1 câu bot chuyển nhân viên (Brain lỗi, gửi lỗi...) trong khoảng ngày
 */
public record ConversationFilter(ChannelType channel, LocalDate from, LocalDate to, boolean hasViolation,
                                 boolean handoff) {

    /** Múi giờ của shop: admin chọn "ngày 18/11" nghĩa là ngày 18/11 giờ Việt Nam. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    Instant fromInstant() {
        return from == null ? null : from.atStartOfDay(ZONE).toInstant();
    }

    /** Mốc loại trừ: đầu ngày sau {@code to}. */
    Instant toInstantExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    Specification<Conversation> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            Instant start = fromInstant();
            Instant end = toInstantExclusive();
            if (channel != null) {
                where.add(cb.equal(root.get("channel"), channel));
            }
            // Khoảng hoạt động [startedAt, lastMessageAt] giao với [from, to + 1 ngày).
            if (start != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("lastMessageAt"), start));
            }
            if (end != null) {
                where.add(cb.lessThan(root.get("startedAt"), end));
            }
            if (hasViolation) {
                where.add(cb.exists(botMessages(root, query, cb, start, end, (m, b) ->
                        b.or(b.isTrue(m.get("blocked")), b.isNotEmpty(m.get("violations"))))));
            }
            if (handoff) {
                where.add(cb.exists(botMessages(root, query, cb, start, end, (m, b) -> b.isTrue(m.get("handoff")))));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private interface MessageCondition {
        Predicate on(Root<ConversationMessage> message, CriteriaBuilder cb);
    }

    private static Subquery<Long> botMessages(Root<Conversation> conversation,
                                              jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb,
                                              Instant start, Instant end, MessageCondition condition) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<ConversationMessage> m = sub.from(ConversationMessage.class);
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(m.get("conversationId"), conversation.get("id")));
        where.add(cb.equal(m.get("direction"), Direction.OUT));
        where.add(condition.on(m, cb));
        if (start != null) {
            where.add(cb.greaterThanOrEqualTo(m.get("createdAt"), start));
        }
        if (end != null) {
            where.add(cb.lessThan(m.get("createdAt"), end));
        }
        return sub.select(m.get("id")).where(where.toArray(Predicate[]::new));
    }
}
