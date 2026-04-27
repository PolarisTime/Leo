package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.AuthUserResponse;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TokenIssuanceService {

    private final UserAccountRepository userAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final PermissionService permissionService;
    private final UserRoleBindingService userRoleBindingService;
    private final SessionManagementService sessionManagementService;
    private final ApplicationEventPublisher eventPublisher;

    public TokenIssuanceService(
            UserAccountRepository userAccountRepository,
            JwtTokenService jwtTokenService,
            PermissionService permissionService,
            UserRoleBindingService userRoleBindingService,
            SessionManagementService sessionManagementService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userAccountRepository = userAccountRepository;
        this.jwtTokenService = jwtTokenService;
        this.permissionService = permissionService;
        this.userRoleBindingService = userRoleBindingService;
        this.sessionManagementService = sessionManagementService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }
        RefreshTokenSession session = sessionManagementService.findActiveSession(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("refreshToken无效或已过期"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now()) || session.isRevoked()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }

        UserAccount user = sessionManagementService.findUserById(session.getUserId());
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        sessionManagementService.revokeSession(session);
        eventPublisher.publishEvent(new SessionInvalidatedEvent(user.getId(), session.getTokenId(), false));
        return issueTokens(user, loginIp, userAgent);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionManagementService.findActiveSession(refreshToken).ifPresent(this::revokeSession);
    }

    void revokeSession(RefreshTokenSession session) {
        sessionManagementService.revokeSession(session);
        eventPublisher.publishEvent(new SessionInvalidatedEvent(session.getUserId(), session.getTokenId(), true));
    }

    TokenResponse issueTokens(UserAccount user, String loginIp, String userAgent) {
        var boundRoles = userRoleBindingService.resolveRolesForUser(user.getId());
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                user.getId(),
                user.getLoginName(),
                userRoleBindingService.toGrantedAuthorities(boundRoles),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getRequireTotpSetup())
        );

        String sessionTokenId = sessionManagementService.newSessionTokenId();
        String accessToken = jwtTokenService.generateAccessToken(principal, sessionTokenId);
        String rawRefreshToken = sessionManagementService.generateRefreshToken();
        sessionManagementService.createSession(user.getId(), sessionTokenId, rawRefreshToken, loginIp, userAgent);

        userAccountRepository.save(user);

        permissionService.evictCache(user.getId());
        var permissions = permissionService.getUserPermissions(user.getId());
        Map<String, String> dataScopes = permissionService.getUserDataScopes(user.getId());
        String currentRoleNames = userRoleBindingService.joinRoleNames(boundRoles);

        eventPublisher.publishEvent(new SessionInvalidatedEvent(user.getId(), sessionTokenId, false));

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenService.getAccessExpirationMs() / 1000,
                new AuthUserResponse(
                        user.getId(),
                        user.getLoginName(),
                        user.getUserName(),
                        currentRoleNames,
                        user.getTotpEnabled(),
                        user.getRequireTotpSetup(),
                        permissions,
                        dataScopes
                )
        );
    }
}
