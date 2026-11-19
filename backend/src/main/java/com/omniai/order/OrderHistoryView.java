package com.omniai.order;

import java.util.List;

/**
 * Lịch sử đơn của 1 khách (T-23.1): {@code orders} mới nhất trước, chỉ gồm kênh đã liên kết; {@code linkedChannels}
 * là các kênh khách đang có danh tính (để bot nói rõ "mình chỉ thấy đơn ở ..."); {@code hasMore} khi còn đơn cũ hơn
 * ngoài {@code limit}.
 */
public record OrderHistoryView(List<OrderView> orders, List<String> linkedChannels, boolean hasMore) {
}
