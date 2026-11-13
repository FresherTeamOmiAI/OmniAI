package com.omniai.order;

import java.time.Instant;
import java.util.List;

/** Đơn trả về cho web (POST/GET /api/orders). {@code notice} nhắc rõ đây là đơn thử nghiệm (SRS UC-06 bước 3). */
public record OrderView(Long id, String status, String channel, List<Line> items, long total, Instant createdAt,
                        String notice) {

    static final String DEMO_NOTICE = "Đơn thử nghiệm: không thanh toán, không giao hàng.";

    public record Line(Long productId, String sku, String name, long unitPrice, int quantity, long lineTotal) {
    }

    static OrderView of(CustomerOrder order) {
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(i.getProductId(), i.getSku(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                .toList();
        String notice = CustomerOrder.STATUS_DEMO.equals(order.getStatus()) ? DEMO_NOTICE : null;
        return new OrderView(order.getId(), order.getStatus(), order.getChannelCode(), lines, order.getTotalAmount(),
                order.getCreatedAt(), notice);
    }
}
