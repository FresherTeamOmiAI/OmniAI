package com.omniai.identity;

import com.omniai.common.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

interface LinkCodeAttemptRepository extends JpaRepository<LinkCodeAttempt, Long> {

    long countByChannelAndExternalIdAndAttemptedAtAfter(ChannelType channel, String externalId, Instant since);

    Optional<LinkCodeAttempt> findFirstByChannelAndExternalIdAndAttemptedAtAfterOrderByAttemptedAtAsc(
            ChannelType channel, String externalId, Instant since);
}
