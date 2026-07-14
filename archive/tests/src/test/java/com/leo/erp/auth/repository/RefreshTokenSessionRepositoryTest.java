package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.enums.RevokeReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionRepositoryTest {

    @Mock
    private RefreshTokenSessionRepository repository;

    @Test
    void findByTokenHashAndDeletedFlagFalse_shouldReturnSessionWhenExists() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(1L);
        session.setTokenHash("hash123");
        session.setDeletedFlag(false);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(repository.findByTokenHashAndDeletedFlagFalse("hash123")).thenReturn(Optional.of(session));

        Optional<RefreshTokenSession> result = repository.findByTokenHashAndDeletedFlagFalse("hash123");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void findByTokenHashAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByTokenHashAndDeletedFlagFalse("hash456")).thenReturn(Optional.empty());

        Optional<RefreshTokenSession> result = repository.findByTokenHashAndDeletedFlagFalse("hash456");

        assertThat(result).isEmpty();
    }

    @Test
    void findByPreviousTokenHashAndDeletedFlagFalse_shouldReturnSessionWhenExists() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(1L);
        session.setTokenHash("hash123");
        session.setPreviousTokenHash("prevhash456");
        session.setDeletedFlag(false);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(repository.findByPreviousTokenHashAndDeletedFlagFalse("prevhash456")).thenReturn(Optional.of(session));

        Optional<RefreshTokenSession> result = repository.findByPreviousTokenHashAndDeletedFlagFalse("prevhash456");

        assertThat(result).isPresent();
        assertThat(result.get().getTokenHash()).isEqualTo("hash123");
    }

    @Test
    void findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc_shouldReturnActiveSessions() {
        RefreshTokenSession session1 = new RefreshTokenSession();
        session1.setUserId(1L);
        session1.setTokenHash("hash1");
        session1.setDeletedFlag(false);
        session1.setExpiresAt(LocalDateTime.now().plusDays(7));

        RefreshTokenSession session2 = new RefreshTokenSession();
        session2.setUserId(1L);
        session2.setTokenHash("hash2");
        session2.setDeletedFlag(false);
        session2.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(repository.findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(session1, session2));

        List<RefreshTokenSession> result = repository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        1L, LocalDateTime.now());

        assertThat(result).hasSize(2);
    }

    @Test
    void countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter_shouldReturnCount() {
        when(repository.countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(2L);

        long count = repository.countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
                1L, LocalDateTime.now());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc_shouldReturnLatestRevokedSession() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(1L);
        session.setTokenHash("hash2");
        session.setDeletedFlag(false);
        session.setRevokeReason(RevokeReason.MANUAL);
        session.setRevokedAt(LocalDateTime.now().minusHours(1));
        session.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(repository.findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc(1L, RevokeReason.MANUAL))
                .thenReturn(Optional.of(session));

        Optional<RefreshTokenSession> result = repository
                .findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc(1L, RevokeReason.MANUAL);

        assertThat(result).isPresent();
        assertThat(result.get().getTokenHash()).isEqualTo("hash2");
    }
}
