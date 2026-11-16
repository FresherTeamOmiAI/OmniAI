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

/**
 * Thương hiệu (V15, decisions #35). Tên duy nhất không phân biệt hoa thường (unique index lower(name)).
 * {@code code} = tiền tố SKU của hãng (CERA, LRP...), null khi không xác định được duy nhất.
 */
@Entity
@Table(name = "brand")
@Getter
@Setter
@NoArgsConstructor
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 8, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    public Brand(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
