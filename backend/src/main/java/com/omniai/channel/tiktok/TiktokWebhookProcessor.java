package com.omniai.channel.tiktok;

import com.omniai.channel.MessageRouter;
import com.omniai.channel.RecentIds;
import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.tiktok.TiktokModels.NewMessageEvent;
import com.omniai.channel.tiktok.TiktokModels.WebhookEnvelope;
import com.omniai.channel.tiktok.TiktokTokenManager.NotAuthorizedException;
import com.omniai.common.BotReply;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import com.omniai.conversation.ConversationMessage.DeliveryStatus;
import com.omniai.conversation.ConversationRecorder;
import com.omniai.policy.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Xử lý webhook TikTok sau khi controller đã trả 200 (chạy trên tiktokWebhookExecutor). Owner: TuTM.
 * NEW_MESSAGE của buyer → MessageRouter (AI Brain + luật TIKTOK) → Send Message.
 * <p>
 * Sàn không gửi lại webhook đã nhận 200, nên gửi tin lỗi thì hub tự lo (T-25.4): lỗi tạm thời (mạng, 5xx, rate limit
 * đã hết lượt ở {@link TiktokApi}) thì thử lại theo {@code send-attempts}, chờ {@code send-retry-backoff} rồi gấp đôi.
 * Lỗi không thử lại được (45109001, không có quyền, chưa cấp scope, shop chưa ủy quyền...) hoặc hết lượt thì câu trả
 * lời được ghi {@code FAILED} + handoff để nhân viên xử lý. Trạng thái từng lần ghi vào conversation_message (V17).
 */
@Component
public class TiktokWebhookProcessor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TiktokWebhookProcessor.class);

    /** Mã lỗi phải chuyển nhân viên, thử lại cũng không qua (docs/channel-api/tiktok.md mục 9). */
    private static final Set<TiktokErrorCode> HANDOFF_ERRORS = Set.of(
            TiktokErrorCode.CS_CONVERSATION_RULES,
            TiktokErrorCode.CS_NO_PERMISSION,
            TiktokErrorCode.SCOPE_NOT_AUTHORIZED);

    private final TiktokChatAdapter adapter;
    private final MessageRouter router;
    private final TiktokShopClient client;
    private final TiktokTokenManager tokens;
    private final PolicyEngine policyEngine;
    private final ConversationRecorder recorder;
    private final int maxAttempts;
    private final Duration backoff;
    private final ScheduledExecutorService retries;
    /** Tin buyer đã trả lời (theo message_id): Send Message không có idempotency key, gửi 2 lần là khách nhận 2 tin. */
    private final RecentIds answered = new RecentIds(10_000);

    public TiktokWebhookProcessor(TiktokChatAdapter adapter, MessageRouter router, TiktokShopClient client,
                                  TiktokTokenManager tokens, PolicyEngine policyEngine, ConversationRecorder recorder,
                                  TiktokProperties props) {
        this.adapter = adapter;
        this.router = router;
        this.client = client;
        this.tokens = tokens;
        this.policyEngine = policyEngine;
        this.recorder = recorder;
        this.maxAttempts = Math.max(1, props.sendAttempts());
        this.backoff = props.sendRetryBackoff();
        this.retries = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "tiktok-send-retry");
            t.setDaemon(true);
            return t;
        });
    }

    public void process(WebhookEnvelope envelope) {
        try {
            switch (envelope.type()) {
                case TiktokWebhookType.NEW_MESSAGE -> onNewMessage(envelope);
                case TiktokWebhookType.NEW_CONVERSATION ->
                        log.info("TikTok: hội thoại mới ở shop {}: {}", envelope.shopId(), envelope.data());
                case TiktokWebhookType.SELLER_DEAUTHORIZATION -> {
                    log.warn("TikTok: seller gỡ ủy quyền shop {}", envelope.shopId());
                    tokens.markStatus(envelope.shopId(), ChannelCredential.Status.REVOKED);
                }
                case TiktokWebhookType.UPCOMING_AUTHORIZATION_EXPIRATION ->
                        log.warn("TikTok: ủy quyền của shop {} sắp hết hạn, nhắc seller ủy quyền lại", envelope.shopId());
                default -> log.debug("TikTok: bỏ qua webhook type {}", envelope.type());
            }
        } catch (RuntimeException e) {
            // Đã trả 200 cho sàn nên sàn không gửi lại; log đủ để tra (S7 thêm dead-letter, US-29).
            log.error("TikTok: xử lý webhook {} (type {}) lỗi: {}", envelope.ttsNotificationId(), envelope.type(),
                    e.getMessage(), e);
        }
    }

    private void onNewMessage(WebhookEnvelope envelope) {
        NewMessageEvent event = TiktokModels.JSON.treeToValue(envelope.data(), NewMessageEvent.class);
        if (!TiktokChatAdapter.isBuyerText(event)) {
            log.debug("TikTok: bỏ qua tin {} (role={}, type={})", event.messageId(),
                    event.sender() == null ? null : event.sender().role(), event.type());
            return;
        }
        if (!answered.markIfNew(event.messageId())) {
            log.info("TikTok: tin {} đã trả lời rồi, bỏ qua", event.messageId());
            return;
        }
        CanonicalMessage message = adapter.toCanonical(
                new TiktokChatAdapter.Inbound(envelope.shopId(), envelope.ttsNotificationId(), event));
        BotReply reply;
        try {
            reply = router.handle(message);
        } catch (RuntimeException e) {
            answered.forget(event.messageId());
            throw e;
        }
        // Đã có câu trả lời: từ đây không forget nữa, việc gửi lại do deliver() lo để khách không nhận 2 tin.
        deliver(new Outgoing(envelope.shopId(), event.conversationId(), message, reply.finalText()), 1);
    }

    private record Outgoing(String shopId, String conversationId, CanonicalMessage message, String text) {
    }

    private void deliver(Outgoing out, int attempt) {
        try {
            String sent = send(out);
            recordDelivery(out, DeliveryStatus.SENT, attempt, null, sent.equals(out.text()) ? null : sent);
        } catch (RuntimeException e) {
            boolean retry = isTransient(e) && attempt < maxAttempts;
            if (retry) {
                long delay = backoff.toMillis() << (attempt - 1);
                log.warn("TikTok: gửi tin trả lời {} lỗi lần {}/{}, thử lại sau {} ms: {}", out.message().messageId(),
                        attempt, maxAttempts, delay, e.getMessage());
                recordDelivery(out, DeliveryStatus.RETRYING, attempt, e.getMessage(), null);
                retries.schedule(() -> deliver(out, attempt + 1), delay, TimeUnit.MILLISECONDS);
            } else {
                log.error("TikTok: không gửi được tin trả lời {} vào hội thoại {} sau {} lần, chuyển nhân viên: {}",
                        out.message().messageId(), out.conversationId(), attempt, e.getMessage());
                recordDelivery(out, DeliveryStatus.FAILED, attempt, e.getMessage(), null);
            }
        }
    }

    /** Gửi câu trả lời, trả lại câu sàn đã nhận (finalText hoặc fallback của kênh). */
    private String send(Outgoing out) {
        try {
            client.sendText(out.shopId(), out.conversationId(), out.text());
            return out.text();
        } catch (TiktokApiException e) {
            if (!e.is(TiktokErrorCode.CS_SENSITIVE_CONTENT)) {
                throw e;
            }
            // Sàn chặn nội dung nhạy cảm mà luật kênh chưa bắt: gửi câu fallback của kênh thay thế.
            log.warn("TikTok chặn nội dung nhạy cảm (request_id={}), gửi fallback", e.requestId());
            String fallback = policyEngine.policyOf(ChannelType.TIKTOK).fallbackMessage();
            client.sendText(out.shopId(), out.conversationId(), fallback);
            return fallback;
        }
    }

    /** Lỗi có thể tự hết: mạng, 5xx, 36009003, rate limit. Lỗi nghiệp vụ, quyền, tham số thì không thử lại. */
    static boolean isTransient(RuntimeException e) {
        if (e instanceof NotAuthorizedException) {
            return false;
        }
        if (e instanceof TiktokApiException api) {
            if (api.error().map(HANDOFF_ERRORS::contains).orElse(false)) {
                return false;
            }
            return api.isRateLimited() || api.is(TiktokErrorCode.INTERNAL_ERROR) || api.httpStatus() >= 500;
        }
        return true;
    }

    private void recordDelivery(Outgoing out, DeliveryStatus status, int attempts, String error, String sentText) {
        try {
            recorder.delivery(out.message(), status, attempts, error, sentText);
        } catch (RuntimeException e) {
            log.error("Không lưu được trạng thái gửi {} của {}: {}", status, out.message().messageId(), e.toString());
        }
    }

    @Override
    public void destroy() {
        // Lượt thử lại đang chờ bị bỏ khi tắt app; câu đó giữ RETRYING trong DB để admin thấy (US-29 làm bền).
        retries.shutdownNow();
    }
}
