package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1 ảnh của sản phẩm (bảng product_image, V15). Tối đa 1 ảnh chính / sản phẩm (unique index một phần). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    /** Khoá trong bucket Supabase, vd. CERA-CLE-01.webp (decisions #26). */
    @Column(name = "storage_key", nullable = false, length = 128)
    private String storageKey;

    /** Ảnh gốc trước khi resize (ghi nguồn CC BY-SA). */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "license_note", length = 300)
    private String licenseNote;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
