package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Store và AI chỉ thấy sản phẩm đang bán (SRS G-06). */
    Optional<Product> findByIdAndActiveTrue(Long id);

    /** Nạp mọi sản phẩm của 1 đơn bằng 1 câu (US-43), thay cho 1 câu / dòng đơn. */
    List<Product> findAllByIdInAndActiveTrue(Collection<Long> ids);

    /** Tra giá và tồn theo SKU cho tool của AI Brain (US-15). Sản phẩm ẩn coi như không có. */
    Optional<Product> findBySkuAndActiveTrue(String sku);

    boolean existsBySku(String sku);
}
