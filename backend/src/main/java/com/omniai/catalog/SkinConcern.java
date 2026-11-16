package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vấn đề da (V15). Sản phẩm nối qua bảng product_skin_concern. Bảng tra cứu cố định, là nguồn chuẩn của bộ từ vựng catalog (decisions #35). */
@Entity
@Table(name = "skin_concern")
@Getter
@NoArgsConstructor
public class SkinConcern {

    @Id
    @Column(length = 30)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
