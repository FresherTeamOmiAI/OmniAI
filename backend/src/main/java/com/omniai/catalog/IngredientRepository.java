package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /** {@code lowerNames} phải đã viết thường. Khớp unique index lower(inci_name) của V15. */
    @Query("select i from Ingredient i where lower(i.inciName) in :lowerNames")
    List<Ingredient> findByLowerNames(@Param("lowerNames") Collection<String> lowerNames);
}
