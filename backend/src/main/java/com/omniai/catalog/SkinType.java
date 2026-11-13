package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Loại da (V15). Sản phẩm nối qua bảng product_skin_type. Bảng tra cứu cố định, là nguồn chuẩn của bộ từ vựng catalog (decisions #35). */
@Entity
@Table(name = "skin_type")
@Getter
@NoArgsConstructor
public class SkinType {

    @Id
    @Column(length = 20)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
