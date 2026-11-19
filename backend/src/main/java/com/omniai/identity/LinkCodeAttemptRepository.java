package com.omniai.identity;

import com.omniai.common.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface LinkCodeAttemptRepository extends JpaRepository<LinkCodeAttempt, Long> {

    long countByChannelAndExternalIdAndAttemptedAtAfter(ChannelType channel, String externalId, Instant since);

    Optional<LinkCodeAttempt> findFirstByChannelAndExternalIdAndAttemptedAtAfterOrderByAttemptedAtAsc(
            ChannelType channel, String externalId, Instant since);

    /** Dọn lần gõ sai cũ ({@link IdentityCleanup}): khoá chỉ đếm trong cửa sổ 1 giờ. */
    @Modifying
    @Query("DELETE FROM LinkCodeAttempt a WHERE a.attemptedAt < :cutoff")
    int deleteAttemptedBefore(@Param("cutoff") Instant cutoff);
}
