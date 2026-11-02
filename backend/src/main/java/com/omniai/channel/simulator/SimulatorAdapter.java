package com.omniai.channel.simulator;

import com.omniai.channel.ChannelAdapter;
import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Giả lập 1 kênh bất kỳ (dùng cho bench và khi chưa có API thật của sàn).
 * Không phải Spring bean vì mỗi request có thể là 1 kênh khác nhau.
 */
public record SimulatorAdapter(ChannelType channel) implements ChannelAdapter<SimulatorRequest> {

    @Override
    public CanonicalMessage toCanonical(SimulatorRequest payload) {
        String messageId = payload.messageId() == null || payload.messageId().isBlank()
                ? UUID.randomUUID().toString()
                : payload.messageId();
        return new CanonicalMessage(
                messageId,
                channel,
                payload.userId(),
                null,
                channel.name().toLowerCase() + ":" + payload.userId(),
                payload.text(),
                Instant.now(),
                Map.of("source", "simulator"));
    }
}
