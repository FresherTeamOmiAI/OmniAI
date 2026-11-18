package com.omniai.channel.shopee;

import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Push code 10 của khách → luật Shopee → trả lời qua {@code v2.sellerchat.send_message} (T-21.6). Owner: ChienPX.
 * Gửi lỗi thì ném lỗi để push trả 500, simulator sẽ gửi lại push.
 */
@Component
public class ShopeePushProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShopeePushProcessor.class);

    private final ShopeeChatAdapter adapter = new ShopeeChatAdapter();
    private final MessageRouter router;
    private final ShopeeChatClient chat;

    public ShopeePushProcessor(MessageRouter router, ShopeeChatClient chat) {
        this.router = router;
        this.chat = chat;
    }

    /** Câu khách nhận, hoặc null nếu push không phải tin chữ của khách. */
    public String onPush(JsonNode push) {
        if (!ShopeeChatAdapter.isBuyerText(push)) {
            log.debug("Shopee: bỏ qua push code {}", push.path("code").asInt());
            return null;
        }
        BotReply reply = router.handle(adapter.toCanonical(push));
        long buyerId = push.path("data").path("content").path("from_id").asLong();
        chat.sendText(buyerId, reply.finalText());
        return reply.finalText();
    }
}
