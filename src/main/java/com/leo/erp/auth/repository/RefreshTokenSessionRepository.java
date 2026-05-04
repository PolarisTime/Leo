package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.enums.RevokeReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long>, JpaSpecificationExecutor<RefreshTokenSession> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenSession> findByTokenHashAndDeletedFlagFalse(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RefreshTokenSession> findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
            Long userId,
            LocalDateTime now
    );

    long countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
            Long userId,
            LocalDateTime now
    );

    List<RefreshTokenSession> findByDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(LocalDateTime now);

    Optional<RefreshTokenSession> findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc(
            Long userId,
            RevokeReason revokeReason
    );
}
