package com.omniai.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lịch sử đơn mọi kênh cho ai-brain (T-23.1; tool {@code get_orders_all_channels} T-23.2 gọi). Dưới /api/internal/**
 * nên cần header {@code X-Internal-Token} (SecurityConfig, decisions #48). {@code customerId} lấy từ
 * {@code ChatRequest.customer_id}. Owner: LucVT.
 */
@RestController
public class InternalOrderController {

    private final OrderHistoryService service;

    public InternalOrderController(OrderHistoryService service) {
        this.service = service;
    }

    @GetMapping("/api/internal/customers/{customerId}/orders")
    public OrderHistoryView history(@PathVariable long customerId,
                                    @RequestParam(defaultValue = "" + OrderHistoryService.DEFAULT_LIMIT) int limit) {
        return service.history(customerId, limit);
    }
}
