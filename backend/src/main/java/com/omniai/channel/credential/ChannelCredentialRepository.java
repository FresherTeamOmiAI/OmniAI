package com.omniai.channel.credential;

import com.omniai.common.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelCredentialRepository extends JpaRepository<ChannelCredential, Long> {

    Optional<ChannelCredential> findByChannelAndShopId(ChannelType channel, String shopId);

    List<ChannelCredential> findByChannelOrderByShopId(ChannelType channel);
}
