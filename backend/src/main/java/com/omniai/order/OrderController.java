package com.omniai.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Đặt hàng ảo cho web store (T-09.3, SRS UC-06). Mở công khai như /api/products: đơn S2 không gắn khách,
 * không có dữ liệu cá nhân (decisions #34). Từ S3 đơn gắn khách đăng nhập thì phải khoá theo chủ đơn.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@Valid @RequestBody OrderRequest request) {
        CustomerOrder order = service.createDemo(request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(OrderView.of(order));
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable Long id) {
        return OrderView.of(service.get(id));
    }
}
