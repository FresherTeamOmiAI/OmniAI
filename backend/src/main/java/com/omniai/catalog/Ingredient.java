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
 * Thành phần INCI (V15, decisions #35). Tên duy nhất không phân biệt hoa thường; lưu cách viết gặp đầu tiên.
 * {@code activeGroup}: nhóm hoạt chất của luật thành phần (retinoid, aha, bha, bpo, vitamin_c, azelaic), null nếu không thuộc nhóm nào.
 */
@Entity
@Table(name = "ingredient")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inci_name", nullable = false, length = 200)
    private String inciName;

    @Column(name = "active_group", length = 20)
    private String activeGroup;

    public Ingredient(String inciName) {
        this.inciName = inciName;
    }
}
