package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Danh mục sản phẩm (V15). Mã khớp cột product.category (FK). Bảng tra cứu cố định, là nguồn chuẩn của bộ từ vựng catalog (decisions #35). */
@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor
public class Category {

    @Id
    @Column(length = 32)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
