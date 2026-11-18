package com.omniai.channel;

import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;

/**
 * Mỗi kênh có 1 adapter: chuyển payload gốc của kênh (tin WebSocket của web, webhook Shopee, TikTok Shop...) về CanonicalMessage.
 * Việc gửi trả lời ra kênh do adapter tự làm theo API của kênh đó.
 *
 * @param <P> kiểu payload gốc của kênh
 */
public interface ChannelAdapter<P> {

    ChannelType channel();

    CanonicalMessage toCanonical(P payload);
}
