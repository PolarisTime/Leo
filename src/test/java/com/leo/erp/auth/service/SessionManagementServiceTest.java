package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionManagementServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private AccessTokenBlacklistService blacklistService;

    @Mock
    private SessionActivityService sessionActivityService;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @Mock
    private AuthProperties authProperties;

    @InjectMocks
    private SessionManagementService service;

    @Test
    void disablingAccount_revokesSessionsAndBlacklistsUserAfterCommit() {
        UserAccount account = new UserAccount();
        account.setId(42L);
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-1");
        session.setUserId(42L);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(42L))
                .thenReturn(Optional.of(account));
        when(refreshTokenSessionRepository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        any(), any()))
                .thenReturn(List.of(session));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(afterCommitExecutor).run(any(Runnable.class));

        service.revokeActiveSessionsForAccountStatusChange(42L);

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.ACCOUNT_DISABLED);
        verify(refreshTokenSessionRepository).save(session);
        verify(blacklistService).blacklistSession("session-1");
        verify(sessionActivityService).clearSession("session-1");
        verify(blacklistService).blacklistUser(42L);
    }
}
