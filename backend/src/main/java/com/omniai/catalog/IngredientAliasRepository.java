package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, IngredientAlias.Key> {

    /** {@code lowerNames} phải đã viết thường. Kết quả theo bí danh rồi seq. */
    @Query("select a from IngredientAlias a where a.aliasLower in :lowerNames order by a.aliasLower, a.seq")
    List<IngredientAlias> findByAliasLowerIn(@Param("lowerNames") Collection<String> lowerNames);
}
