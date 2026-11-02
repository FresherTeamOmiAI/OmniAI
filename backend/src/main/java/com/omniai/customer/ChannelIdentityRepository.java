package com.omniai.customer;

import com.omniai.common.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelIdentityRepository extends JpaRepository<ChannelIdentity, Long> {

    Optional<ChannelIdentity> findByChannelAndExternalId(ChannelType channel, String externalId);

    List<ChannelIdentity> findByCustomerId(Long customerId);
}
