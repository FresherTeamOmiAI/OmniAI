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
import java.util.Map;

/** API đọc catalog cho web store và AI Brain, chỉ trả sản phẩm active. CRUD admin: AdminProductController. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository repository;
    private final ProductService service;

    public ProductController(ProductRepository repository, ProductService service) {
        this.repository = repository;
        this.service = service;
    }

    /**
     * Danh sách store (T-09.2): chỉ sản phẩm active. Lọc tuỳ chọn, kết hợp AND: {@code skinType} (1 loại da),
     * {@code category}, {@code minPrice}/{@code maxPrice} (giá web, VND, gồm cả mốc). Sai thì 400 ProblemDetail.
     */
    @GetMapping
    public PageResponse<Product> list(@RequestParam(required = false) String skinType,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) Long minPrice,
                                      @RequestParam(required = false) Long maxPrice,
                                      @PageableDefault(size = 20) Pageable pageable) {
        Page<Product> page = service.storeSearch(new StoreFilter(skinType, category, minPrice, maxPrice), pageable);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * Giá theo kênh và tồn kho của một SKU đang bán (US-15). AI Brain gọi endpoint này, không đọc cột stock
     * trên view chatbot. Sản phẩm ẩn trả 404.
     */
    @GetMapping("/sku/{sku}")
    public ResponseEntity<PriceStockView> bySku(@PathVariable String sku) {
        return repository.findBySkuAndActiveTrue(sku)
                .map(product -> ResponseEntity.ok(PriceStockView.from(product)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Đủ field để vẽ trang giả lập Shopee/TikTok. Cùng quy tắc ẩn như GET /{id}. */
    @GetMapping("/sku/{sku}/card")
    public ResponseEntity<Product> card(@PathVariable String sku) {
        return ResponseEntity.of(repository.findBySkuAndActiveTrue(sku));
    }

    /** Sản phẩm ẩn trả 404 như không tồn tại (SRS G-06). Admin xem qua /api/admin/products/{id}. */
    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable Long id) {
        return ResponseEntity.of(repository.findByIdAndActiveTrue(id));
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {
    }

    public record PriceStockView(String sku, String brand, String name, String category,
                                 Map<String, Long> prices, int stock) {
        static PriceStockView from(Product product) {
            return new PriceStockView(product.getSku(), product.getBrand(), product.getName(),
                    product.getCategory(), product.getPrices(), product.getStock());
        }
    }
}
