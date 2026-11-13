package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Store và AI chỉ thấy sản phẩm đang bán (SRS G-06). */
    Optional<Product> findByIdAndActiveTrue(Long id);

    boolean existsBySku(String sku);
}
