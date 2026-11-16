package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkinConcernRepository extends JpaRepository<SkinConcern, String> {

    List<SkinConcern> findAllByOrderBySortOrderAsc();
}
