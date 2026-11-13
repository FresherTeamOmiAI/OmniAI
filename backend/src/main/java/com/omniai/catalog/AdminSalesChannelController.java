package com.omniai.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Danh sách kênh bán cho form admin sản phẩm (V13, decisions #32). Cần tài khoản admin như /api/admin/**. */
@RestController
@RequestMapping("/api/admin/sales-channels")
public class AdminSalesChannelController {

    private final ProductService service;

    public AdminSalesChannelController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<SalesChannel> list() {
        return service.channels();
    }
}
