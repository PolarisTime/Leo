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
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionManagementService {

    private static final int DEFAULT_MAX_REFRESH_TOKENS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final SystemSwitchService systemSwitchService;
    private final AuthProperties authProperties;

    public SessionManagementService(
            UserAccountRepository userAccountRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            JwtTokenService jwtTokenService,
            SnowflakeIdGenerator snowflakeIdGenerator,
            AccessTokenBlacklistService blacklistService,
            SessionActivityService sessionActivityService,
            AfterCommitExecutor afterCommitExecutor,
            @org.springframework.lang.Nullable SystemSwitchService systemSwitchService,
            AuthProperties authProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.systemSwitchService = systemSwitchService;
        this.authProperties = authProperties != null ? authProperties : new AuthProperties();
    }

    private int maxRefreshTokensPerUser() {
        if (systemSwitchService == null) return DEFAULT_MAX_REFRESH_TOKENS;
        return systemSwitchService.getMaxConcurrentSessions();
    }

    @Transactional
    public RefreshTokenSession createSession(Long userId, String sessionTokenId, String rawRefreshToken,
                                              String loginIp, String userAgent) {
        UserAccount user = findUserByIdForUpdate(userId);
        trimActiveSessionsBeforeIssuing(userId);

        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(snowflakeIdGenerator.nextId());
        session.setUserId(userId);
        session.setTokenId(sessionTokenId);
        session.setTokenHash(hashToken(rawRefreshToken));
        session.setCredentialVersion(normalizeCredentialVersion(user.getCredentialVersion()));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshExpirationMs() / 1000));
        session.setLoginIp(loginIp);
        session.setDeviceInfo(userAgent);
        refreshTokenSessionRepository.save(session);
        sessionActivityService.touchSession(sessionTokenId);
        return session;
    }

    @Transactional
    public RefreshTokenRotationResult refreshSession(RefreshTokenSession session,
                                                    String currentRefreshToken,
                                                    String loginIp,
                                                    String userAgent) {
        String nextRefreshToken = authProperties.getRefreshToken().isRotationEnabled()
                ? generateRefreshToken()
                : currentRefreshToken;
        String currentHash = session.getTokenHash();
        if (authProperties.getRefreshToken().isRotationEnabled()) {
            session.setTokenHash(hashToken(nextRefreshToken));
            session.setPreviousTokenHash(currentHash);
            session.setPreviousTokenValidUntil(LocalDateTime.now().plusSeconds(refreshTokenReuseGraceSeconds()));
        } else {
            session.setPreviousTokenHash(null);
            session.setPreviousTokenValidUntil(null);
        }
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshExpirationMs() / 1000));
        session.setLoginIp(loginIp);
        session.setDeviceInfo(userAgent);
        RefreshTokenSession saved = refreshTokenSessionRepository.save(session);
        sessionActivityService.touchSession(session.getTokenId());
        return new RefreshTokenRotationResult(saved, nextRefreshToken);
    }

    @Transactional
    public void revokeSession(RefreshTokenSession session) {
        revokeSession(session, RevokeReason.MANUAL);
    }

    @Transactional
    public void revokeSession(RefreshTokenSession session, RevokeReason reason) {
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokeReason(reason);
        refreshTokenSessionRepository.save(session);
        scheduleSessionRevocationSideEffects(session.getTokenId());
    }

    public Optional<RefreshTokenSession> findActiveSession(String refreshToken) {
        return refreshTokenSessionRepository.findByTokenHashAndDeletedFlagFalse(hashToken(refreshToken))
                .filter(session -> !session.isRevoked());
    }

    public Optional<Long> findRefreshTokenUserId(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        return refreshTokenSessionRepository.findUserIdByTokenHash(tokenHash)
                .or(() -> refreshTokenSessionRepository.findUserIdByPreviousTokenHash(tokenHash));
    }

    public Optional<RefreshTokenSession> findPreviousTokenSession(String refreshToken) {
        return refreshTokenSessionRepository.findByPreviousTokenHashAndDeletedFlagFalse(hashToken(refreshToken))
                .filter(session -> !session.isRevoked());
    }

    public boolean isPreviousTokenInGraceWindow(RefreshTokenSession session) {
        LocalDateTime validUntil = session.getPreviousTokenValidUntil();
        return validUntil != null && !validUntil.isBefore(LocalDateTime.now());
    }

    public UserAccount findUserById(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
    }

    public UserAccount findUserByIdForUpdate(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
    }

    @Transactional
    public void revokeActiveSessionsForPasswordChange(Long userId) {
        findUserByIdForUpdate(userId);
        var activeSessions = refreshTokenSessionRepository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        userId,
                        LocalDateTime.now()
                );
        for (RefreshTokenSession session : activeSessions) {
            revokeSession(session, RevokeReason.PASSWORD_CHANGED);
        }
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String newSessionTokenId() {
        return UUID.randomUUID().toString();
    }

    private long refreshTokenReuseGraceSeconds() {
        return Math.max(0L, authProperties.getRefreshToken().getReuseGraceSeconds());
    }

    private long normalizeCredentialVersion(Long credentialVersion) {
        return credentialVersion == null ? 0L : credentialVersion;
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    private void scheduleSessionRevocationSideEffects(String sessionId) {
        afterCommitExecutor.run(() -> {
            blacklistService.blacklistSession(sessionId);
            sessionActivityService.clearSession(sessionId);
        });
    }

    private void trimActiveSessionsBeforeIssuing(Long userId) {
        int maxSessions = maxRefreshTokensPerUser();
        if (maxSessions <= 0) {
            return;
        }
        var activeTokens = refreshTokenSessionRepository
                .findByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(userId, LocalDateTime.now());
        int limitBeforeCreate = maxSessions - 1;
        if (activeTokens.size() <= limitBeforeCreate) {
            return;
        }

        int toRevoke = activeTokens.size() - limitBeforeCreate;
        LocalDateTime revokedAt = LocalDateTime.now();
        for (int i = 0; i < toRevoke; i++) {
            RefreshTokenSession token = activeTokens.get(i);
            token.setRevokedAt(revokedAt);
            token.setRevokeReason(RevokeReason.CONCURRENT_LIMIT);
            refreshTokenSessionRepository.save(token);
            scheduleSessionRevocationSideEffects(token.getTokenId());
        }
    }

    public Optional<RefreshTokenSession> findSessionByHash(String rawToken) {
        return refreshTokenSessionRepository.findByTokenHashAndDeletedFlagFalse(hashToken(rawToken));
    }

    public Optional<RefreshTokenSession> findRecentlyEvictedSession(Long userId) {
        return refreshTokenSessionRepository
                .findFirstByUserIdAndRevokeReasonAndDeletedFlagFalseOrderByRevokedAtDesc(
                        userId, RevokeReason.CONCURRENT_LIMIT);
    }

    public record RefreshTokenRotationResult(
            RefreshTokenSession session,
            String refreshToken
    ) {
    }
}
