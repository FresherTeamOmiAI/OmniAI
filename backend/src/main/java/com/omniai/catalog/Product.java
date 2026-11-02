package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sản phẩm mỹ phẩm. Giá lưu theo từng kênh (VND). Owner: LucVT. */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String brand;

    /** vd. sunscreen, cleanser, serum, moisturizer, toner */
    private String category;

    /** Loại da phù hợp, phân tách bằng dấu phẩy: oily,dry,combination,sensitive,normal */
    @Column(name = "skin_types")
    private String skinTypes;

    /** Danh sách thành phần INCI, phân tách bằng dấu phẩy. */
    @Column(name = "ingredients_inci", length = 4000)
    private String ingredientsInci;

    @Column(name = "size_ml")
    private Integer sizeMl;

    /** Period After Opening: dùng trong bao nhiêu tháng sau khi mở. */
    @Column(name = "pao_months")
    private Integer paoMonths;

    @Column(name = "price_web")
    private Long priceWeb;

    @Column(name = "price_shopee")
    private Long priceShopee;

    @Column(name = "price_tiktok")
    private Long priceTiktok;

    private int stock;

    private boolean active = true;
}
