package com.omniai.channel.tiktok;

import com.omniai.channel.MessageRouter;
import com.omniai.channel.RecentIds;
import com.omniai.channel.credential.ChannelCredential;
import com.omniai.channel.tiktok.TiktokModels.NewMessageEvent;
import com.omniai.channel.tiktok.TiktokModels.WebhookEnvelope;
import com.omniai.common.BotReply;
import com.omniai.common.ChannelType;
import com.omniai.policy.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Xử lý webhook TikTok sau khi controller đã trả 200 (chạy trên tiktokWebhookExecutor). Owner: TuTM.
 * NEW_MESSAGE của buyer → MessageRouter (AI Brain + luật TIKTOK) → Send Message.
 */
@Component
public class TiktokWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(TiktokWebhookProcessor.class);

    private final TiktokChatAdapter adapter;
    private final MessageRouter router;
    private final TiktokShopClient client;
    private final TiktokTokenManager tokens;
    private final PolicyEngine policyEngine;
    /** Tin buyer đã trả lời (theo message_id): Send Message không có idempotency key, gửi 2 lần là khách nhận 2 tin. */
    private final RecentIds answered = new RecentIds(10_000);

    public TiktokWebhookProcessor(TiktokChatAdapter adapter, MessageRouter router, TiktokShopClient client,
                                  TiktokTokenManager tokens, PolicyEngine policyEngine) {
        this.adapter = adapter;
        this.router = router;
        this.client = client;
        this.tokens = tokens;
        this.policyEngine = policyEngine;
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
        BotReply reply = router.handle(adapter.toCanonical(
                new TiktokChatAdapter.Inbound(envelope.shopId(), envelope.ttsNotificationId(), event)));
        try {
            send(envelope.shopId(), event.conversationId(), reply.finalText());
        } catch (RuntimeException e) {
            answered.forget(event.messageId());
            throw e;
        }
    }

    private void send(String shopId, String conversationId, String text) {
        try {
            client.sendText(shopId, conversationId, text);
        } catch (TiktokApiException e) {
            if (e.is(TiktokErrorCode.CS_SENSITIVE_CONTENT)) {
                // Sàn chặn nội dung nhạy cảm mà luật kênh chưa bắt: gửi câu fallback của kênh thay thế.
                log.warn("TikTok chặn nội dung nhạy cảm (request_id={}), gửi fallback", e.requestId());
                client.sendText(shopId, conversationId, policyEngine.policyOf(ChannelType.TIKTOK).fallbackMessage());
            } else if (e.is(TiktokErrorCode.CS_CONVERSATION_RULES)) {
                log.warn("TikTok không cho gửi tin vào hội thoại {} (request_id={}), cần nhân viên", conversationId,
                        e.requestId());
            } else if (e.is(TiktokErrorCode.SCOPE_NOT_AUTHORIZED)) {
                log.error("Shop {} chưa cấp scope seller.customer_service (request_id={})", shopId, e.requestId());
            } else {
                throw e;
            }
        }
    }
}
