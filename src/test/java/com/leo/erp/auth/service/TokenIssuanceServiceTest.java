package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.verify;
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

    private UserAccount normalUser() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("admin");
        user.setUserName("管理员");
        user.setStatus(com.leo.erp.auth.domain.enums.UserStatus.NORMAL);
        user.setTotpEnabled(false);
        user.setRequireTotpSetup(false);
        return user;
    }
}
