package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagementServiceTest {

    @Test
    void createSessionShouldSaveAndReturnSession() {
        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepo.findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                any(), any())).thenReturn(List.of());

        SessionManagementService service = createService(sessionRepo);

        RefreshTokenSession session = service.createSession(1L, "session-id", "raw-token", "127.0.0.1", "JUnit");

        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getTokenId()).isEqualTo("session-id");
        assertThat(session.getTokenHash()).isNotBlank();
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(sessionRepo).save(any());
    }

    @Test
    void findActiveSessionShouldReturnEmptyWhenRevoked() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setRevokedAt(LocalDateTime.now());

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByTokenHashAndDeletedFlagFalse(any())).thenReturn(Optional.of(session));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findActiveSession("token")).isEmpty();
    }

    @Test
    void findActiveSessionShouldReturnSessionWhenActive() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenHash(SessionManagementService.hashToken("token"));

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByTokenHashAndDeletedFlagFalse(any())).thenReturn(Optional.of(session));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findActiveSession("token")).isPresent();
    }

    @Test
    void revokeSessionShouldSetRevokedAt() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-id");

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionManagementService service = createService(sessionRepo);

        service.revokeSession(session);

        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.MANUAL);
        verify(sessionRepo).save(session);
    }

    @Test
    void findUserByIdShouldThrowWhenNotFound() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        SessionManagementService service = createService(null, userRepo);

        assertThatThrownBy(() -> service.findUserById(99L))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void isPreviousTokenInGraceWindowShouldReturnTrueWhenValid() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setPreviousTokenValidUntil(LocalDateTime.now().plusSeconds(30));

        SessionManagementService service = createService(null);

        assertThat(service.isPreviousTokenInGraceWindow(session)).isTrue();
    }

    @Test
    void isPreviousTokenInGraceWindowShouldReturnFalseWhenExpired() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setPreviousTokenValidUntil(LocalDateTime.now().minusSeconds(1));

        SessionManagementService service = createService(null);

        assertThat(service.isPreviousTokenInGraceWindow(session)).isFalse();
    }

    @Test
    void generateRefreshTokenShouldReturnBase64String() {
        SessionManagementService service = createService(null);

        String token = service.generateRefreshToken();

        assertThat(token).isNotBlank();
        assertThat(token.length()).isGreaterThan(60);
    }

    @Test
    void hashTokenShouldReturnConsistentHash() {
        String hash1 = SessionManagementService.hashToken("token");
        String hash2 = SessionManagementService.hashToken("token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void refreshSessionShouldRotateTokenWhenEnabled() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-id");
        session.setTokenHash(SessionManagementService.hashToken("old-token"));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthProperties authProperties = new AuthProperties();
        authProperties.getRefreshToken().setRotationEnabled(true);

        SessionManagementService service = createService(sessionRepo, null, authProperties);

        SessionManagementService.RefreshTokenRotationResult result =
                service.refreshSession(session, "old-token", "new-ip", "new-agent");

        assertThat(result.session()).isNotNull();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo("old-token");
        assertThat(session.getPreviousTokenHash()).isNotNull();
    }

    @Test
    void constructorShouldUseDefaultAuthPropertiesWhenNull() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-id");
        session.setTokenHash(SessionManagementService.hashToken("old-token"));

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionManagementService service = createService(sessionRepo, null, null);

        SessionManagementService.RefreshTokenRotationResult result =
                service.refreshSession(session, "old-token", "127.0.0.1", "JUnit");

        assertThat(result.refreshToken()).isNotEqualTo("old-token");
        assertThat(session.getPreviousTokenHash()).isEqualTo(SessionManagementService.hashToken("old-token"));
    }

    @Test
    void createSessionShouldUseSystemSwitchMaxSessionsAndEvictOldest() {
        RefreshTokenSession oldSession = activeSession("old-session");
        RefreshTokenSession keptSession = activeSession("kept-session");
        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                anyLong(), any())).thenReturn(List.of(oldSession, keptSession));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        when(systemSwitchService.getMaxConcurrentSessions()).thenReturn(2);
        SessionManagementService service = createService(sessionRepo, null, new AuthProperties(), systemSwitchService);

        service.createSession(1L, "new-session", "raw-token", "127.0.0.1", "JUnit");

        assertThat(oldSession.getRevokedAt()).isNotNull();
        assertThat(oldSession.getRevokeReason()).isEqualTo(RevokeReason.CONCURRENT_LIMIT);
        assertThat(keptSession.getRevokedAt()).isNull();
        verify(systemSwitchService).getMaxConcurrentSessions();
        verify(sessionRepo).save(oldSession);
    }

    @Test
    void createSessionShouldSkipTrimmingWhenMaxSessionsIsZeroOrNegative() {
        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        when(systemSwitchService.getMaxConcurrentSessions()).thenReturn(0);
        SessionManagementService service = createService(sessionRepo, null, new AuthProperties(), systemSwitchService);

        service.createSession(1L, "session-id", "raw-token", "127.0.0.1", "JUnit");

        verify(sessionRepo, org.mockito.Mockito.never())
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(anyLong(), any());
    }

    @Test
    void refreshSessionShouldKeepCurrentRefreshTokenWhenRotationDisabled() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-id");
        session.setTokenHash(SessionManagementService.hashToken("current-token"));
        session.setPreviousTokenHash("previous-hash");
        session.setPreviousTokenValidUntil(LocalDateTime.now().plusSeconds(30));

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthProperties authProperties = new AuthProperties();
        authProperties.getRefreshToken().setRotationEnabled(false);

        SessionManagementService service = createService(sessionRepo, null, authProperties);

        SessionManagementService.RefreshTokenRotationResult result =
                service.refreshSession(session, "current-token", "new-ip", "new-agent");

        assertThat(result.refreshToken()).isEqualTo("current-token");
        assertThat(session.getTokenHash()).isEqualTo(SessionManagementService.hashToken("current-token"));
        assertThat(session.getPreviousTokenHash()).isNull();
        assertThat(session.getPreviousTokenValidUntil()).isNull();
        assertThat(session.getLoginIp()).isEqualTo("new-ip");
        assertThat(session.getDeviceInfo()).isEqualTo("new-agent");
    }

    @Test
    void findPreviousTokenSessionShouldFilterRevokedSession() {
        RefreshTokenSession revoked = new RefreshTokenSession();
        revoked.setRevokedAt(LocalDateTime.now());

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByPreviousTokenHashAndDeletedFlagFalse(any())).thenReturn(Optional.of(revoked));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findPreviousTokenSession("previous-token")).isEmpty();
    }

    @Test
    void findPreviousTokenSessionShouldReturnActiveSession() {
        RefreshTokenSession active = activeSession("session-id");

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByPreviousTokenHashAndDeletedFlagFalse(any())).thenReturn(Optional.of(active));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findPreviousTokenSession("previous-token")).contains(active);
    }

    @Test
    void isPreviousTokenInGraceWindowShouldReturnFalseWhenWindowMissing() {
        RefreshTokenSession session = new RefreshTokenSession();

        SessionManagementService service = createService(null);

        assertThat(service.isPreviousTokenInGraceWindow(session)).isFalse();
    }

    @Test
    void refreshSessionShouldClampNegativeReuseGraceToNow() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId("session-id");
        session.setTokenHash(SessionManagementService.hashToken("old-token"));

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthProperties authProperties = new AuthProperties();
        authProperties.getRefreshToken().setReuseGraceSeconds(-10);
        SessionManagementService service = createService(sessionRepo, null, authProperties);

        LocalDateTime beforeRefresh = LocalDateTime.now();
        service.refreshSession(session, "old-token", "127.0.0.1", "JUnit");

        assertThat(session.getPreviousTokenValidUntil())
                .isAfterOrEqualTo(beforeRefresh)
                .isBefore(LocalDateTime.now().plusSeconds(2));
    }

    @Test
    void findSessionByHashShouldDelegateToRepositoryWithHash() {
        RefreshTokenSession session = activeSession("session-id");

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByTokenHashAndDeletedFlagFalse(SessionManagementService.hashToken("raw-token")))
                .thenReturn(Optional.of(session));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findSessionByHash("raw-token")).contains(session);
    }

    @Test
    void findRecentlyEvictedSessionShouldUseConcurrentLimitReason() {
        RefreshTokenSession evicted = activeSession("evicted-session");
        evicted.setRevokeReason(RevokeReason.CONCURRENT_LIMIT);

        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc(
                1L, RevokeReason.CONCURRENT_LIMIT)).thenReturn(Optional.of(evicted));

        SessionManagementService service = createService(sessionRepo);

        assertThat(service.findRecentlyEvictedSession(1L)).contains(evicted);
    }

    @Test
    void hashTokenShouldWrapMissingSha256Algorithm() {
        try (MockedStatic<MessageDigest> digest = Mockito.mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing algorithm"));

            assertThatThrownBy(() -> SessionManagementService.hashToken("raw-token"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256不可用");
        }
    }

    @Test
    void createSessionShouldUseDefaultMaxSessionsWhenSystemSwitchMissing() {
        RefreshTokenSessionRepository sessionRepo = mock(RefreshTokenSessionRepository.class);
        when(sessionRepo.findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                anyLong(), any())).thenReturn(List.of());
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SessionManagementService service = createService(sessionRepo);

        service.createSession(1L, "session-id", "raw-token", "127.0.0.1", "JUnit");

        verify(sessionRepo).findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                anyLong(), any());
    }

    private SessionManagementService createService(RefreshTokenSessionRepository sessionRepo) {
        return createService(sessionRepo, null, new AuthProperties());
    }

    private SessionManagementService createService(RefreshTokenSessionRepository sessionRepo, UserAccountRepository userRepo) {
        return createService(sessionRepo, userRepo, new AuthProperties());
    }

    private SessionManagementService createService(RefreshTokenSessionRepository sessionRepo,
                                                    UserAccountRepository userRepo,
                                                    AuthProperties authProperties) {
        return createService(sessionRepo, userRepo, authProperties, null);
    }

    private SessionManagementService createService(RefreshTokenSessionRepository sessionRepo,
                                                    UserAccountRepository userRepo,
                                                    AuthProperties authProperties,
                                                    SystemSwitchService systemSwitchService) {
        JwtTokenService jwtService = mock(JwtTokenService.class);
        when(jwtService.getRefreshExpirationMs()).thenReturn(1_800_000L);

        AccessTokenBlacklistService blacklistService = mock(AccessTokenBlacklistService.class);
        SessionActivityService activityService = mock(SessionActivityService.class);
        AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();

        return new SessionManagementService(
                userRepo != null ? userRepo : mock(UserAccountRepository.class),
                sessionRepo != null ? sessionRepo : mock(RefreshTokenSessionRepository.class),
                jwtService,
                new SnowflakeIdGenerator(0L),
                blacklistService,
                activityService,
                afterCommitExecutor,
                systemSwitchService,
                authProperties
        );
    }

    private RefreshTokenSession activeSession(String tokenId) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenId(tokenId);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return session;
    }
}
