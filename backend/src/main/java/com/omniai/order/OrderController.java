package com.omniai.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Đặt hàng ảo cho web store (T-09.3, SRS UC-06). Từ S3 cần đăng nhập: {@code Authorization: Bearer <JWT>} của người mua
 * (SecurityConfig), đơn gắn {@code customer_id} và chỉ chủ đơn xem được (SRS G-04, decisions #40).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequest request) {
        CustomerOrder order = service.createDemo(request, customerId(jwt));
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(OrderView.of(order));
    }

    @GetMapping("/{id}")
    public OrderView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return OrderView.of(service.getOwned(id, customerId(jwt)));
    }

    private static long customerId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
