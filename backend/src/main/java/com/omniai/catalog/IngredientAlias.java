package com.omniai.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

/**
 * Bí danh tên INCI -> tên chuẩn EU/ASEAN (V24, T-27.4, decisions #54). Chỉ đọc: bảng sinh bởi
 * {@code tools/catalog/normalize_inci.py}. {@code canonical} null = token rác (mã lô, mảnh vỡ), bỏ đi;
 * 1 bí danh dính nhiều tên thì nhiều dòng theo {@code seq}.
 */
@Entity
@Table(name = "ingredient_alias")
@IdClass(IngredientAlias.Key.class)
@Immutable
@Getter
@NoArgsConstructor
public class IngredientAlias {

    @Id
    @Column(name = "alias_lower")
    private String aliasLower;

    @Id
    private short seq;

    private String canonical;

    public record Key(String aliasLower, short seq) implements Serializable {
    }
}
