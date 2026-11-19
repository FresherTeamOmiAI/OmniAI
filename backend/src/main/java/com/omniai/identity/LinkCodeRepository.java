package com.omniai.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface LinkCodeRepository extends JpaRepository<LinkCode, Long> {

    boolean existsByCodeHash(String codeHash);

    /** Khoá dòng để 2 danh tính gõ cùng 1 mã cùng lúc thì chỉ 1 bên nhận được. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LinkCode> findByCodeHash(String codeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LinkCode> findByIdAndCustomerId(Long id, Long customerId);

    List<LinkCode> findByCustomerIdAndClaimedAtNotNullAndResolutionIsNullOrderByClaimedAtDesc(Long customerId);

    /** Lấy mã mới thì thu hồi mã cũ chưa ai gõ. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE LinkCode c SET c.revokedAt = :now WHERE c.customerId = :customerId "
            + "AND c.claimedAt IS NULL AND c.revokedAt IS NULL AND c.expiresAt > :now")
    int revokeUnclaimed(@Param("customerId") Long customerId, @Param("now") Instant now);

    /**
     * Dọn mã cũ ({@link IdentityCleanup}): mã lấy trước {@code cutoff} đã quá cả hạn gõ lẫn hạn xác nhận.
     * Giữ mã {@code keep} (CONFIRMED): đó là vết danh tính sàn nào được gắn bằng mã nào, lúc nào.
     */
    @Modifying
    @Query("DELETE FROM LinkCode c WHERE c.createdAt < :cutoff AND (c.resolution IS NULL OR c.resolution <> :keep)")
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff, @Param("keep") LinkCode.Resolution keep);
}
