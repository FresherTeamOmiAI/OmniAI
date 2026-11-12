package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Sản phẩm mỹ phẩm. Giá lưu theo từng kênh (VND). Owner: LucVT. Schema: V1 + V4 + V9. */
@Entity
@Table(name = "product")
@EntityListeners(ProductImages.class)
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

    @Column(nullable = false)
    private String brand;

    /** vd. sunscreen, cleanser, serum, moisturizer, toner */
    @Column(nullable = false)
    private String category;

    /** Loại da phù hợp, phân tách bằng dấu phẩy: oily,dry,combination,sensitive,normal */
    @Column(name = "skin_types", nullable = false)
    private String skinTypes;

    /** Vấn đề da sản phẩm nhắm tới, phân tách bằng dấu phẩy: acne,pores,dark-spots,... (xem data-model.md) */
    @Column(name = "skin_concerns")
    private String skinConcerns;

    /** Buổi dùng trong routine: am | pm | am,pm */
    @Column(name = "usage_time", length = 10)
    private String usageTime;

    /** Mô tả ngắn tiếng Việt (công dụng, kết cấu, cách dùng), dùng cho trang chi tiết và RAG. */
    @Column(length = 2000)
    private String description;

    /** Danh sách thành phần INCI, phân tách bằng dấu phẩy. */
    @Column(name = "ingredients_inci", nullable = false, length = 4000)
    private String ingredientsInci;

    @Column(name = "size_ml", nullable = false)
    private Integer sizeMl;

    /** Period After Opening: dùng trong bao nhiêu tháng sau khi mở. */
    @Column(name = "pao_months", nullable = false)
    private Integer paoMonths;

    @Column(name = "price_web", nullable = false)
    private Long priceWeb;

    @Column(name = "price_shopee", nullable = false)
    private Long priceShopee;

    @Column(name = "price_tiktok", nullable = false)
    private Long priceTiktok;

    private int stock;

    private boolean active = true;

    /** Mã vạch trên Open Beauty Facts (T-47.1). NULL với sản phẩm admin tự tạo. */
    @Column(length = 32, unique = true)
    private String barcode;

    /** Trang nguồn của dữ liệu, vd. https://world.openbeautyfacts.org/product/<barcode>. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** Khoá ảnh trong bucket Supabase, dạng <SKU>.webp (decisions #26). URL đầy đủ ở {@link #imageUrl}. */
    @Column(name = "image_key", length = 128)
    private String imageKey;

    /** Ảnh gốc trước khi resize (để ghi nguồn CC BY-SA). */
    @Column(name = "image_source_url", length = 500)
    private String imageSourceUrl;

    /** Nguồn và giấy phép của ảnh/dữ liệu, phải hiện khi hiển thị ảnh. */
    @Column(name = "image_license_note", length = 300)
    private String imageLicenseNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** URL public của ảnh, do {@link ProductImages} ghép từ cấu hình Supabase + imageKey. Không lưu DB. */
    @Transient
    private String imageUrl;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
