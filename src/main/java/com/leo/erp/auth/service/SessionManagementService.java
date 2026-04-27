package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
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

    private static final int MAX_REFRESH_TOKENS_PER_USER = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AccessTokenBlacklistService blacklistService;
    private final SessionActivityService sessionActivityService;
    private final AfterCommitExecutor afterCommitExecutor;

    public SessionManagementService(
            UserAccountRepository userAccountRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            JwtTokenService jwtTokenService,
            SnowflakeIdGenerator snowflakeIdGenerator,
            AccessTokenBlacklistService blacklistService,
            SessionActivityService sessionActivityService,
            AfterCommitExecutor afterCommitExecutor
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.blacklistService = blacklistService;
        this.sessionActivityService = sessionActivityService;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Transactional
    public RefreshTokenSession createSession(Long userId, String sessionTokenId, String rawRefreshToken,
                                              String loginIp, String userAgent) {
        trimActiveSessionsBeforeIssuing(userId);

        RefreshTokenSession session = new RefreshTokenSession();
        session.setId(snowflakeIdGenerator.nextId());
        session.setUserId(userId);
        session.setTokenId(sessionTokenId);
        session.setTokenHash(hashToken(rawRefreshToken));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshExpirationMs() / 1000));
        session.setLoginIp(loginIp);
        session.setDeviceInfo(userAgent);
        refreshTokenSessionRepository.save(session);
        sessionActivityService.touchSession(sessionTokenId);
        return session;
    }

    @Transactional
    public void revokeSession(RefreshTokenSession session) {
        session.setRevokedAt(LocalDateTime.now());
        refreshTokenSessionRepository.save(session);
        scheduleSessionRevocationSideEffects(session.getTokenId());
    }

    public Optional<RefreshTokenSession> findActiveSession(String refreshToken) {
        return refreshTokenSessionRepository.findByTokenHashAndDeletedFlagFalse(hashToken(refreshToken))
                .filter(session -> !session.isRevoked());
    }

    public UserAccount findUserById(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String newSessionTokenId() {
        return UUID.randomUUID().toString();
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
}
