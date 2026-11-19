package com.omniai.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    /**
     * Mã mới nhất của email, khoá dòng tới hết transaction để 2 request song song (đoán mã, gửi lại)
     * không cùng vượt giới hạn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByEmailOrderByCreatedAtDescIdDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    Optional<OtpChallenge> findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc(String email, Instant after);

    /** Dọn mã cũ ({@link IdentityCleanup}): mã gửi trước {@code cutoff} đã hết hạn, hết khoá và nằm ngoài cửa sổ 1 giờ. */
    @Modifying
    @Query("DELETE FROM OtpChallenge c WHERE c.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
