package com.omniai.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Thẻ sản phẩm cho các dòng gợi ý trong câu trả lời của chat widget (US-43, T-43.3). Widget không còn tải cả catalog
 * để ghép ảnh: backend tra đúng các SKU AI Brain gợi ý, 1 câu SQL. Owner: LucVT.
 */
@Service
public class ProductSuggestions {

    /** Khớp {@code MAX_CARD_PRODUCTS} của ai-brain. */
    public static final int MAX_CARDS = 5;

    private final ProductRepository products;

    public ProductSuggestions(ProductRepository products) {
        this.products = products;
    }

    /** Thẻ theo đúng thứ tự SKU, bỏ trùng, bỏ SKU không có hoặc đã ẩn, tối đa {@link #MAX_CARDS}. */
    @Transactional(readOnly = true)
    public List<ProductCard> cards(List<String> skus) {
        List<String> wanted = skus == null ? List.of() : skus.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(sku -> !sku.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().limit(MAX_CARDS).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<String, Product> bySku = products.findAllBySkuInAndActiveTrue(wanted).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity(), (a, b) -> a));
        return wanted.stream().map(bySku::get).filter(Objects::nonNull).map(ProductCard::of).toList();
    }
}
