package com.omniai.channel;

import com.omniai.common.CanonicalMessage;
import com.omniai.common.ChannelType;

/**
 * Mỗi kênh có 1 adapter: chuyển payload gốc của kênh (webhook Telegram, Shopee push...) về CanonicalMessage.
 * Việc gửi trả lời ra kênh do adapter tự làm theo API của kênh đó.
 *
 * @param <P> kiểu payload gốc của kênh
 */
public interface ChannelAdapter<P> {

    ChannelType channel();

    CanonicalMessage toCanonical(P payload);
}
