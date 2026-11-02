package com.omniai.common;

import java.time.Instant;
import java.util.Map;

/**
 * Tin nhắn vào đã chuẩn hóa. Mọi adapter kênh chuyển payload gốc về dạng này.
 * Schema dùng chung với Python: shared/schemas/canonical-message.schema.json
 */
public record CanonicalMessage(
        String messageId,
        ChannelType channel,
        String channelUserId,
        Long customerId,
        String conversationId,
        String text,
        Instant receivedAt,
        Map<String, Object> metadata) {

    public CanonicalMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
