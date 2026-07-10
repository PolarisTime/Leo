package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenIssuanceServiceTest {

    @Test
    void issueTokensShouldReturnValidResponse() {
        UserAccount user = normalUser();
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.save(any())).thenReturn(user);

        JwtTokenService jwtService = mock(JwtTokenService.class);
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessExpirationMs()).thenReturn(300_000L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(1_800_000L);

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.getUserPermissions(1L)).thenReturn(List.of());
        when(permissionService.getUserDataScopes(1L)).thenReturn(Map.of());

        UserRoleBindingService roleBinding = mock(UserRoleBindingService.class);
        RoleSetting role = new RoleSetting();
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");
        when(roleBinding.resolveRolesForUser(1L)).thenReturn(List.of(role));
        when(roleBinding.toGrantedAuthorities(List.of(role))).thenReturn(List.of());
        when(roleBinding.joinRoleNames(List.of(role))).thenReturn("管理员");

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        when(sessionMgmt.newSessionTokenId()).thenReturn("session-id");
        when(sessionMgmt.generateRefreshToken()).thenReturn("refresh-token");
        RefreshTokenSession createdSession = session(1L, "session-id");
        createdSession.setCredentialVersion(6L);
        when(sessionMgmt.createSession(1L, "session-id", "refresh-token", "127.0.0.1", "JUnit"))
                .thenReturn(createdSession);

        TokenIssuanceService service = new TokenIssuanceService(
                userRepo, jwtService, permissionService,
                roleBinding, sessionMgmt, mock(ApplicationEventPublisher.class)
        );

        TokenResponse response = service.issueTokens(user, "127.0.0.1", "JUnit");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user()).isNotNull();
        assertThat(response.user().loginName()).isEqualTo("admin");
        org.mockito.ArgumentCaptor<SecurityPrincipal> principalCaptor =
                org.mockito.ArgumentCaptor.forClass(SecurityPrincipal.class);
        verify(jwtService).generateAccessToken(principalCaptor.capture(), org.mockito.ArgumentMatchers.eq("session-id"));
        assertThat(principalCaptor.getValue().credentialVersion()).isEqualTo(6L);
    }

    @Test
    void logoutShouldRevokeActiveSession() {
        com.leo.erp.auth.domain.entity.RefreshTokenSession session = new com.leo.erp.auth.domain.entity.RefreshTokenSession();
        session.setUserId(1L);
        session.setTokenId("session-id");

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        when(sessionMgmt.findActiveSession("refresh-token")).thenReturn(Optional.of(session));

        TokenIssuanceService service = new TokenIssuanceService(
                mock(UserAccountRepository.class), mock(JwtTokenService.class),
                mock(PermissionService.class), mock(UserRoleBindingService.class),
                sessionMgmt, mock(ApplicationEventPublisher.class)
        );

        service.logout("refresh-token");

        verify(sessionMgmt).revokeSession(session);
    }

    @Test
    void logoutShouldDoNothingWhenTokenBlank() {
        SessionManagementService sessionMgmt = mock(SessionManagementService.class);

        TokenIssuanceService service = new TokenIssuanceService(
                mock(UserAccountRepository.class), mock(JwtTokenService.class),
                mock(PermissionService.class), mock(UserRoleBindingService.class),
                sessionMgmt, mock(ApplicationEventPublisher.class)
        );

        service.logout(null);
        service.logout("");
        service.logout("   ");

        verify(sessionMgmt, org.mockito.Mockito.never()).findActiveSession(anyString());
    }

    @Test
    void refreshShouldRejectNullOrBlankTokenBeforeRepositoryLookup() {
        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh(null, "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.refresh("", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.refresh("   ", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(sessionMgmt);
    }

    @Test
    void refreshShouldLockUserBeforeSessionAndRejectStaleCredentialVersion() {
        RefreshTokenSession session = session(1L, "session-id");
        session.setCredentialVersion(2L);
        UserAccount user = normalUser();
        user.setCredentialVersion(3L);
        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        when(sessionMgmt.findRefreshTokenUserId("refresh-token")).thenReturn(Optional.of(1L));
        when(sessionMgmt.findUserByIdForUpdate(1L)).thenReturn(user);
        when(sessionMgmt.findActiveSession("refresh-token")).thenReturn(Optional.of(session));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("refresh-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("凭据已变更");

        var ordered = inOrder(sessionMgmt);
        ordered.verify(sessionMgmt).findRefreshTokenUserId("refresh-token");
        ordered.verify(sessionMgmt).findUserByIdForUpdate(1L);
        ordered.verify(sessionMgmt).findActiveSession("refresh-token");
        verify(sessionMgmt, never()).refreshSession(any(), anyString(), anyString(), anyString());
    }

    @Test
    void refreshShouldRejectUnknownTokenWhenNoSessionExists() {
        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("missing-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);

        verify(sessionMgmt).findRefreshTokenUserId("missing-token");
        verify(sessionMgmt, never()).findActiveSession("missing-token");
    }

    @Test
    void refreshShouldReportEvictedSessionWhenHashWasConcurrentLimited() {
        RefreshTokenSession evicted = session(1L, "session-id");
        evicted.setRevokeReason(RevokeReason.CONCURRENT_LIMIT);

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "evicted-token", normalUser());
        when(sessionMgmt.findActiveSession("evicted-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findPreviousTokenSession("evicted-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findSessionByHash("evicted-token")).thenReturn(Optional.of(evicted));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("evicted-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_EVICTED);
    }

    @Test
    void refreshShouldRejectHashedSessionWhenRevokeReasonIsNotConcurrentLimit() {
        RefreshTokenSession reused = session(1L, "session-id");
        reused.setRevokeReason(RevokeReason.REUSE_DETECTED);

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "reused-hash-token", normalUser());
        when(sessionMgmt.findActiveSession("reused-hash-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findPreviousTokenSession("reused-hash-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findSessionByHash("reused-hash-token")).thenReturn(Optional.of(reused));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("reused-hash-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshShouldRejectPreviousTokenInsideGraceWindow() {
        RefreshTokenSession previous = session(1L, "session-id");

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "old-token", normalUser());
        when(sessionMgmt.findActiveSession("old-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findPreviousTokenSession("old-token")).thenReturn(Optional.of(previous));
        when(sessionMgmt.isPreviousTokenInGraceWindow(previous)).thenReturn(true);
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("old-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_CONFLICT);

        verify(sessionMgmt).isPreviousTokenInGraceWindow(previous);
        verify(sessionMgmt, never()).findSessionByHash("old-token");
        verify(sessionMgmt, never()).revokeSession(any(), any());
    }

    @Test
    void refreshShouldRevokePreviousTokenOutsideGraceWindow() {
        RefreshTokenSession previous = session(1L, "session-id");
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "reused-token", normalUser());
        when(sessionMgmt.findActiveSession("reused-token")).thenReturn(Optional.empty());
        when(sessionMgmt.findPreviousTokenSession("reused-token")).thenReturn(Optional.of(previous));
        when(sessionMgmt.isPreviousTokenInGraceWindow(previous)).thenReturn(false);
        TokenIssuanceService service = createService(sessionMgmt, eventPublisher);

        assertThatThrownBy(() -> service.refresh("reused-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);

        verify(sessionMgmt).revokeSession(previous, RevokeReason.REUSE_DETECTED);
        verify(eventPublisher).publishEvent(any(SessionInvalidatedEvent.class));
    }

    @Test
    void refreshShouldRejectExpiredActiveSession() {
        RefreshTokenSession expired = session(1L, "session-id");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "expired-token", normalUser());
        when(sessionMgmt.findActiveSession("expired-token")).thenReturn(Optional.of(expired));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("expired-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);

        verify(sessionMgmt).findUserByIdForUpdate(1L);
    }

    @Test
    void refreshShouldRejectRevokedActiveSession() {
        RefreshTokenSession revoked = session(1L, "session-id");
        revoked.setRevokedAt(LocalDateTime.now());

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "revoked-token", normalUser());
        when(sessionMgmt.findActiveSession("revoked-token")).thenReturn(Optional.of(revoked));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("revoked-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BadCredentialsException.class);

        verify(sessionMgmt).findUserByIdForUpdate(1L);
    }

    @Test
    void refreshShouldRejectDisabledUser() {
        RefreshTokenSession session = session(1L, "session-id");
        UserAccount disabledUser = normalUser();
        disabledUser.setStatus(UserStatus.DISABLED);

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "refresh-token", disabledUser);
        when(sessionMgmt.findActiveSession("refresh-token")).thenReturn(Optional.of(session));
        TokenIssuanceService service = createService(sessionMgmt);

        assertThatThrownBy(() -> service.refresh("refresh-token", "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(sessionMgmt, never()).refreshSession(any(), anyString(), anyString(), anyString());
    }

    @Test
    void refreshShouldRotateSessionAndIssueAccessToken() {
        RefreshTokenSession session = session(1L, "session-id");
        UserAccount user = normalUser();

        JwtTokenService jwtService = mock(JwtTokenService.class);
        when(jwtService.generateAccessToken(any(SecurityPrincipal.class), anyString())).thenReturn("access-token");
        when(jwtService.getAccessExpirationMs()).thenReturn(300_000L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(1_800_000L);

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.getUserPermissions(1L)).thenReturn(List.of());
        when(permissionService.getUserDataScopes(1L)).thenReturn(Map.of());

        UserRoleBindingService roleBinding = mock(UserRoleBindingService.class);
        when(roleBinding.resolveRolesForUser(1L)).thenReturn(List.of());
        when(roleBinding.toGrantedAuthorities(List.of())).thenReturn(List.of());
        when(roleBinding.joinRoleNames(List.of())).thenReturn("");

        SessionManagementService sessionMgmt = mock(SessionManagementService.class);
        stubRefreshOwner(sessionMgmt, "refresh-token", user);
        when(sessionMgmt.findActiveSession("refresh-token")).thenReturn(Optional.of(session));
        when(sessionMgmt.refreshSession(session, "refresh-token", "127.0.0.1", "JUnit"))
                .thenReturn(new SessionManagementService.RefreshTokenRotationResult(session, "rotated-refresh-token"));

        TokenIssuanceService service = new TokenIssuanceService(
                mock(UserAccountRepository.class),
                jwtService,
                permissionService,
                roleBinding,
                sessionMgmt,
                mock(ApplicationEventPublisher.class)
        );

        TokenResponse response = service.refresh("refresh-token", "127.0.0.1", "JUnit");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("rotated-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(300L);
        assertThat(response.refreshExpiresIn()).isEqualTo(1_800L);
        verify(sessionMgmt).refreshSession(session, "refresh-token", "127.0.0.1", "JUnit");
        verify(permissionService).evictCache(1L);
    }

    private UserAccount normalUser() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("admin");
        user.setUserName("管理员");
        user.setStatus(UserStatus.NORMAL);
        user.setTotpEnabled(false);
        user.setRequireTotpSetup(false);
        return user;
    }

    private void stubRefreshOwner(SessionManagementService sessionManagementService,
                                  String refreshToken,
                                  UserAccount user) {
        when(sessionManagementService.findRefreshTokenUserId(refreshToken))
                .thenReturn(Optional.of(user.getId()));
        when(sessionManagementService.findUserByIdForUpdate(user.getId())).thenReturn(user);
    }

    private RefreshTokenSession session(Long userId, String tokenId) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setUserId(userId);
        session.setTokenId(tokenId);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return session;
    }

    private TokenIssuanceService createService(SessionManagementService sessionMgmt) {
        return createService(sessionMgmt, mock(ApplicationEventPublisher.class));
    }

    private TokenIssuanceService createService(SessionManagementService sessionMgmt,
                                               ApplicationEventPublisher eventPublisher) {
        return new TokenIssuanceService(
                mock(UserAccountRepository.class),
                mock(JwtTokenService.class),
                mock(PermissionService.class),
                mock(UserRoleBindingService.class),
                sessionMgmt,
                eventPublisher
        );
    }
}
