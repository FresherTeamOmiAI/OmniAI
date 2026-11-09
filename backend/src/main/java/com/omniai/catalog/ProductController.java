package com.omniai.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API đọc catalog cho web store và AI Brain, chỉ trả sản phẩm active. CRUD admin: AdminProductController. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<Product> list(@RequestParam(required = false) String skinType,
                                      @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page = skinType == null || skinType.isBlank()
                ? repository.findByActiveTrue(pageable)
                : repository.findByActiveTrueAndSkinTypesContainingIgnoreCase(skinType, pageable);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Sản phẩm ẩn trả 404 như không tồn tại (SRS G-06). Admin xem qua /api/admin/products/{id}. */
    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable Long id) {
        return ResponseEntity.of(repository.findByIdAndActiveTrue(id));
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {
    }
}
