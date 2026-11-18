package com.omniai.channel.shopee;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Shop trả lời khách qua {@code v2.sellerchat.send_message} có ký (T-21.6). Trước đây hub ghi thẳng vào DB simulator.
 * Owner: ChienPX.
 */
@Component
public class ShopeeChatClient {

    static final String SEND_MESSAGE_PATH = "/api/v2/sellerchat/send_message";

    private final ShopeeApiClient api;

    public ShopeeChatClient(ShopeeApiClient api) {
        this.api = api;
    }

    /** Gửi tin chữ tới buyer. Trả {@code message_id} Shopee cấp. */
    public String sendText(long buyerId, String text) {
        JsonNode response = api.post(SEND_MESSAGE_PATH, Map.of(
                "to_id", buyerId,
                "message_type", "text",
                "content", Map.of("text", text)));
        return response.path("message_id").asString("");
    }
}
