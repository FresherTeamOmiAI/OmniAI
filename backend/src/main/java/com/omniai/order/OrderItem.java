package com.omniai.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1 dòng đơn. SKU, tên, đơn giá được chụp lại lúc đặt, không đổi khi sản phẩm đổi sau này. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false)
    private long lineTotal;
}
