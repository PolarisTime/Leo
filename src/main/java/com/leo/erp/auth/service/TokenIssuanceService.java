package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.AuthUserResponse;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TokenIssuanceService {

    private static final int MAX_REFRESH_TOKENS_PER_USER = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final PermissionService permissionService;
    private final UserRoleBindingService userRoleBindingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final DashboardSummaryService dashboardSummaryService;

    public TokenIssuanceService(
            UserAccountRepository userAccountRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            JwtTokenService jwtTokenService,
            SnowflakeIdGenerator snowflakeIdGenerator,
            AccessTokenBlacklistService blacklistService,
            SessionActivityService sessionActivityService,
            PermissionService permissionService,
            UserRoleBindingService userRoleBindingService,
            AfterCommitExecutor afterCommitExecutor,
            DashboardSummaryService dashboardSummaryService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.permissionService = permissionService;
        this.userRoleBindingService = userRoleBindingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }
        RefreshTokenSession session = findActiveSession(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("refreshToken无效或已过期"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now()) || session.isRevoked()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }

        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(session.getUserId())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));

        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        session.setRevokedAt(LocalDateTime.now());
        scheduleSessionRevocationSideEffects(session.getTokenId());
        return issueTokens(user, loginIp, userAgent, session);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        findActiveSession(refreshToken).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            refreshTokenSessionRepository.save(session);
            scheduleSessionRevocationSideEffects(session.getTokenId());
            if (dashboardSummaryService != null) {
                dashboardSummaryService.evictCache(session.getUserId());
            }
        });
    }

    TokenResponse issueTokens(UserAccount user, String loginIp, String userAgent, RefreshTokenSession previousSession) {
        List<com.leo.erp.system.role.domain.entity.RoleSetting> boundRoles = userRoleBindingService.resolveRolesForUser(user.getId());
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                user.getId(),
                user.getLoginName(),
                userRoleBindingService.toGrantedAuthorities(boundRoles),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getRequireTotpSetup())
        );

        String sessionTokenId = UUID.randomUUID().toString();
        String accessToken = jwtTokenService.generateAccessToken(principal, sessionTokenId);
        String rawRefreshToken = generateRefreshToken();
        if (previousSession != null) {
            refreshTokenSessionRepository.save(previousSession);
        }
        trimActiveSessionsBeforeIssuing(user.getId());

        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(snowflakeIdGenerator.nextId());
        session.setUserId(user.getId());
        session.setTokenId(sessionTokenId);
        session.setTokenHash(hashToken(rawRefreshToken));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshExpirationMs() / 1000));
        session.setLoginIp(loginIp);
        session.setDeviceInfo(userAgent);
        refreshTokenSessionRepository.save(session);
        sessionActivityService.touchSession(sessionTokenId);

        userAccountRepository.save(user);

        permissionService.evictCache(user.getId());
        var permissions = permissionService.getUserPermissions(user.getId());
        Map<String, String> dataScopes = permissionService.getUserDataScopes(user.getId());
        String currentRoleNames = userRoleBindingService.joinRoleNames(boundRoles);
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictCache(user.getId());
        }

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

    Optional<RefreshTokenSession> findActiveSession(String refreshToken) {
        return refreshTokenSessionRepository.findByTokenHashAndDeletedFlagFalse(hashToken(refreshToken))
                .filter(session -> !session.isRevoked());
    }

    UserAccount findUserById(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
    }

    void scheduleSessionRevocationSideEffects(String sessionId) {
        afterCommitExecutor.run(() -> {
            blacklistService.blacklistSession(sessionId);
            sessionActivityService.clearSession(sessionId);
        });
    }

    private void trimActiveSessionsBeforeIssuing(Long userId) {
        var activeTokens = refreshTokenSessionRepository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(userId, LocalDateTime.now());
        int limitBeforeCreate = MAX_REFRESH_TOKENS_PER_USER - 1;
        if (activeTokens.size() <= limitBeforeCreate) {
            return;
        }

        int toRevoke = activeTokens.size() - limitBeforeCreate;
        LocalDateTime revokedAt = LocalDateTime.now();
        for (int i = 0; i < toRevoke; i++) {
            RefreshTokenSession token = activeTokens.get(i);
            token.setRevokedAt(revokedAt);
            refreshTokenSessionRepository.save(token);
            scheduleSessionRevocationSideEffects(token.getTokenId());
        }
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }
}
