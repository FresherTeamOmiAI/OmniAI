package com.omniai.order;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Người mua xem đơn của mình ở mọi kênh đã liên kết (T-23.1, US-23). Dưới /api/me/** nên cần JWT người mua
 * (SecurityConfig.customerFilterChain); khách = {@code sub} của token. Owner: LucVT.
 */
@RestController
public class MeOrderController {

    private final OrderHistoryService service;

    public MeOrderController(OrderHistoryService service) {
        this.service = service;
    }

    @GetMapping("/api/me/orders")
    public OrderHistoryView history(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "" + OrderHistoryService.DEFAULT_LIMIT) int limit) {
        return service.history(Long.parseLong(jwt.getSubject()), limit);
    }
}
