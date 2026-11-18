package com.omniai.channel.shopee;

import com.omniai.channel.ChannelAdapter;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

/** Push webchat code 10 của Shopee → tin chuẩn. Owner: ChienPX. */
public class ShopeeChatAdapter implements ChannelAdapter<JsonNode> {

    @Override
    public ChannelType channel() {
        return ChannelType.SHOPEE;
    }

    @Override
    public CanonicalMessage toCanonical(JsonNode push) {
        JsonNode content = push.path("data").path("content");
        long created = content.path("created_timestamp").asLong(0);
        return new CanonicalMessage(
                content.path("message_id").asString(),
                ChannelType.SHOPEE,
                content.path("from_id").asString(),
                null,
                "shopee:" + content.path("conversation_id").asString(),
                content.path("content").path("text").asString(""),
                created > 0 ? Instant.ofEpochSecond(created) : Instant.now(),
                Map.of("source", "shopee-push",
                        "shopId", Long.toString(push.path("shop_id").asLong()),
                        "shopeeConversationId", content.path("conversation_id").asString()));
    }

    /** Tin chữ của khách. Tin của shop thì bỏ qua để khỏi trả lời vòng lặp. */
    public static boolean isBuyerText(JsonNode push) {
        if (push == null || push.path("code").asInt() != 10) {
            return false;
        }
        JsonNode content = push.path("data").path("content");
        if (!"text".equals(content.path("message_type").asString())) {
            return false;
        }
        if (content.path("content").path("text").asString("").isBlank()) {
            return false;
        }
        int fromShop = content.path("from_shop_id").asInt(0);
        return fromShop == 0;
    }
}
