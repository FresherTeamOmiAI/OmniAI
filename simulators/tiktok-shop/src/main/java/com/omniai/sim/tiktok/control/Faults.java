package com.omniai.sim.tiktok.control;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lỗi bật/tắt được qua POST /sim/faults để test kịch bản biên (docs/channel-api/tiktok.md mục 9).
 * Mặc định tắt hết. POST /sim/reset đưa về mặc định.
 */
@Component
public class Faults {

    /** Mỗi request API mở ngủ thêm chừng này ms (để dồn request đồng thời, thử rate limit). */
    private volatile long apiLatencyMs;
    /** Ngủ trước khi gửi webhook (thử webhook trễ). */
    private volatile long webhookDelayMs;
    /** Gửi mỗi webhook 2 lần với cùng tts_notification_id (thử chống trùng). */
    private volatile boolean webhookDuplicate;
    /** Tin TEXT chứa một trong các từ này thì Send Message trả 45101006. */
    private final Set<String> sensitiveWords = new CopyOnWriteArraySet<>();
    /** Scope bị seller thu hồi: API cần scope này trả SCOPE_NOT_AUTHORIZED. */
    private final Set<String> revokedScopes = new CopyOnWriteArraySet<>();
    /** Hội thoại mới tạo có can_send_message=false: Send Message trả 45109001. */
    private volatile boolean blockSending;

    public record Snapshot(long apiLatencyMs, long webhookDelayMs, boolean webhookDuplicate,
                           List<String> sensitiveWords, List<String> revokedScopes, boolean blockSending) {
    }

    /** Body của POST /sim/faults: trường null thì giữ nguyên. */
    public record Update(Long apiLatencyMs, Long webhookDelayMs, Boolean webhookDuplicate,
                         List<String> sensitiveWords, List<String> revokedScopes, Boolean blockSending) {
    }

    public synchronized Snapshot apply(Update u) {
        if (u.apiLatencyMs() != null) apiLatencyMs = Math.max(0, u.apiLatencyMs());
        if (u.webhookDelayMs() != null) webhookDelayMs = Math.max(0, u.webhookDelayMs());
        if (u.webhookDuplicate() != null) webhookDuplicate = u.webhookDuplicate();
        if (u.blockSending() != null) blockSending = u.blockSending();
        if (u.sensitiveWords() != null) {
            sensitiveWords.clear();
            u.sensitiveWords().forEach(w -> sensitiveWords.add(w.toLowerCase()));
        }
        if (u.revokedScopes() != null) {
            revokedScopes.clear();
            revokedScopes.addAll(u.revokedScopes());
        }
        return snapshot();
    }

    public synchronized void reset() {
        apply(new Update(0L, 0L, false, List.of(), List.of(), false));
    }

    public Snapshot snapshot() {
        return new Snapshot(apiLatencyMs, webhookDelayMs, webhookDuplicate,
                List.copyOf(sensitiveWords), List.copyOf(revokedScopes), blockSending);
    }

    public long apiLatencyMs() {
        return apiLatencyMs;
    }

    public long webhookDelayMs() {
        return webhookDelayMs;
    }

    public boolean webhookDuplicate() {
        return webhookDuplicate;
    }

    public boolean blockSending() {
        return blockSending;
    }

    public boolean isRevoked(String scope) {
        return revokedScopes.contains(scope);
    }

    public boolean containsSensitive(String text) {
        String lower = text.toLowerCase();
        return sensitiveWords.stream().anyMatch(lower::contains);
    }
}
