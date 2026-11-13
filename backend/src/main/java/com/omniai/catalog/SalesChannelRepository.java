package com.omniai.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesChannelRepository extends JpaRepository<SalesChannel, String> {

    List<SalesChannel> findAllByOrderByOwnedDescCodeAsc();
}
