package com.omniai.brain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body gửi sang ai-brain POST /chat (snake_case cho khớp Pydantic). */
public record BrainRequest(
        @JsonProperty("message_id") String messageId,
        String channel,
        @JsonProperty("channel_owned") boolean channelOwned,
        @JsonProperty("user_id") String userId,
        @JsonProperty("customer_id") Long customerId,
        String text) {
}
