package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkinTypeRepository extends JpaRepository<SkinType, String> {

    List<SkinType> findAllByOrderBySortOrderAsc();
}
