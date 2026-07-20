package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.RefreshTokenSession;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.RevokeReason;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.AuthUserResponse;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TokenIssuanceService {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final UserAccountRepository userAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final SessionManagementService sessionManagementService;
    private final ApplicationEventPublisher eventPublisher;

    public TokenIssuanceService(
            UserAccountRepository userAccountRepository,
            JwtTokenService jwtTokenService,
            SessionManagementService sessionManagementService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userAccountRepository = userAccountRepository;
        this.jwtTokenService = jwtTokenService;
        this.sessionManagementService = sessionManagementService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }
        Optional<Long> userId = sessionManagementService.findRefreshTokenUserId(refreshToken);
        if (userId.isEmpty()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }

        UserAccount user = sessionManagementService.findUserByIdForUpdate(userId.get());
        Optional<RefreshTokenSession> activeOpt = sessionManagementService.findActiveSession(refreshToken);

        if (activeOpt.isEmpty()) {
            Optional<RefreshTokenSession> previousSession = sessionManagementService.findPreviousTokenSession(refreshToken);
            if (previousSession.isPresent()) {
                throw previousRefreshTokenException(previousSession.get());
            }
            Optional<RefreshTokenSession> anySession = sessionManagementService.findSessionByHash(refreshToken);
            if (anySession.isPresent() && anySession.get().getRevokeReason() == RevokeReason.CONCURRENT_LIMIT) {
                throw new BusinessException(ErrorCode.SESSION_EVICTED, ErrorCode.SESSION_EVICTED.getMessage());
            }
            throw new BadCredentialsException("refreshToken无效或已过期");
        }

        RefreshTokenSession session = activeOpt.get();

        if (session.getExpiresAt().isBefore(LocalDateTime.now()) || session.isRevoked()) {
            throw new BadCredentialsException("refreshToken无效或已过期");
        }

        if (user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }
        if (normalizeCredentialVersion(session.getCredentialVersion())
                != normalizeCredentialVersion(user.getCredentialVersion())) {
            throw new BadCredentialsException("凭据已变更，请重新登录");
        }

        SessionManagementService.RefreshTokenRotationResult refreshResult =
                sessionManagementService.refreshSession(session, refreshToken, loginIp, userAgent);
        return issueAccessTokenForSession(user, refreshResult.session(), refreshResult.refreshToken());
    }

    private RuntimeException previousRefreshTokenException(RefreshTokenSession session) {
        if (sessionManagementService.isPreviousTokenInGraceWindow(session)) {
            return new BusinessException(
                    ErrorCode.REFRESH_TOKEN_REUSE_CONFLICT,
                    ErrorCode.REFRESH_TOKEN_REUSE_CONFLICT.getMessage()
            );
        }
        sessionManagementService.revokeSession(session, RevokeReason.REUSE_DETECTED);
        eventPublisher.publishEvent(new SessionInvalidatedEvent(session.getUserId(), session.getTokenId(), true));
        return new BadCredentialsException("refreshToken无效或已过期");
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionManagementService.findActiveSessionForLogout(refreshToken).ifPresent(this::revokeSession);
    }

    void revokeSession(RefreshTokenSession session) {
        sessionManagementService.revokeSession(session);
        eventPublisher.publishEvent(new SessionInvalidatedEvent(session.getUserId(), session.getTokenId(), true));
    }

    TokenResponse issueTokens(UserAccount user, String loginIp, String userAgent) {
        String sessionTokenId = sessionManagementService.newSessionTokenId();
        String rawRefreshToken = sessionManagementService.generateRefreshToken();
        RefreshTokenSession session = sessionManagementService.createSession(
                user.getId(),
                sessionTokenId,
                rawRefreshToken,
                loginIp,
                userAgent
        );
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                user.getId(),
                user.getLoginName(),
                normalizeCredentialVersion(session.getCredentialVersion())
        );
        String accessToken = jwtTokenService.generateAccessToken(principal, sessionTokenId);

        userAccountRepository.save(user);

        eventPublisher.publishEvent(new SessionInvalidatedEvent(user.getId(), sessionTokenId, false));

        long refreshExpiresIn = jwtTokenService.getRefreshExpirationMs() / MILLIS_PER_SECOND;

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessExpirationMs() / MILLIS_PER_SECOND,
                refreshExpiresIn,
                new AuthUserResponse(
                        user.getId(),
                        user.getLoginName(),
                        user.getUserName()
                )
        );
    }

    private TokenResponse issueAccessTokenForSession(UserAccount user,
                                                     RefreshTokenSession session,
                                                     String rawRefreshToken) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                user.getId(),
                user.getLoginName(),
                normalizeCredentialVersion(user.getCredentialVersion())
        );
        String accessToken = jwtTokenService.generateAccessToken(principal, session.getTokenId());

        long refreshExpiresIn = jwtTokenService.getRefreshExpirationMs() / MILLIS_PER_SECOND;

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessExpirationMs() / MILLIS_PER_SECOND,
                refreshExpiresIn,
                new AuthUserResponse(
                        user.getId(),
                        user.getLoginName(),
                        user.getUserName()
                )
        );
    }

    private long normalizeCredentialVersion(Long credentialVersion) {
        return credentialVersion == null ? 0L : credentialVersion;
    }
}
