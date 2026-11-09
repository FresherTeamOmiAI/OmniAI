package com.omniai.catalog;

import com.omniai.catalog.ProductController.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API quản trị sản phẩm (US-08, UC-20). Cần HTTP Basic tài khoản admin (SecurityConfig, SRS Q-01).
 * Không có DELETE: sản phẩm chỉ bị ẩn (G-07).
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService service;

    public AdminProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Product> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean active,
                                      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<Product> page = service.search(q, active, pageable);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        Product created = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public Product setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }

    public record ActiveRequest(@NotNull(message = "bắt buộc") Boolean active) {
    }
}
