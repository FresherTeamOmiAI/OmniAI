package com.omniai.channel.shopee;

import com.omniai.channel.MessageRouter;
import com.omniai.common.BotReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Push code 10 của khách → luật Shopee → tin trả lời ghi vào simulator. Owner: ChienPX.
 */
@Component
public class ShopeePushProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShopeePushProcessor.class);

    private final ShopeeChatAdapter adapter = new ShopeeChatAdapter();
    private final MessageRouter router;
    private final ShopeeSimulatorStore store;
    private final JsonMapper json = JsonMapper.builder().build();

    public ShopeePushProcessor(MessageRouter router, ShopeeSimulatorStore store) {
        this.router = router;
        this.store = store;
    }

    /** Câu khách nhận, hoặc null nếu push không phải tin chữ của khách. */
    public String onPush(JsonNode push) {
        if (!ShopeeChatAdapter.isBuyerText(push)) {
            log.debug("Shopee: bỏ qua push code {}", push.path("code").asInt());
            return null;
        }
        JsonNode content = push.path("data").path("content");
        BotReply reply = router.handle(adapter.toCanonical(push));
        long shopId = push.path("shop_id").asLong();
        long buyerId = content.path("from_id").asLong();
        String buyerName = content.path("from_user_name").asString("");
        ObjectNode body = json.createObjectNode();
        body.put("text", reply.finalText());
        store.appendMessage(shopId, buyerId, buyerName, false,
                shopId, "", buyerId, buyerName,
                "text", json.writeValueAsString(body), "normal",
                java.time.Instant.now().getEpochSecond());
        return reply.finalText();
    }
}
