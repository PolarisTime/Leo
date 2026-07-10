package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LoginStep1Response;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {

    private static final String TEMP_TOKEN_PREFIX = "auth:2fa:temp:";
    private static final String TEMP_TOKEN_USER_INDEX_PREFIX = "auth:2fa:user-temp:";
    private static final String TEMP_TOKEN_VALUE_VERSION = "v1";
    private static final Duration TEMP_TOKEN_TTL = Duration.ofMinutes(5);

    /** 认证请求上下文，封装重复出现的请求元数据 */
    public record AuthRequestContext(String loginIp, String userAgent, String requestPath, String requestMethod) {
    }

    private record TempTokenChallenge(long userId, long credentialVersion) {
    }

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final LoginAttemptService loginAttemptService;
    private final StringRedisTemplate redisTemplate;
    private final TokenIssuanceService tokenIssuanceService;
    private final OperationLogService operationLogService;
    private final SystemSwitchService systemSwitchService;
    private final CaptchaService captchaService;

    public LoginService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            LoginAttemptService loginAttemptService,
            StringRedisTemplate redisTemplate,
            TokenIssuanceService tokenIssuanceService,
            OperationLogService operationLogService,
            SystemSwitchService systemSwitchService,
            CaptchaService captchaService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.loginAttemptService = loginAttemptService;
        this.redisTemplate = redisTemplate;
        this.tokenIssuanceService = tokenIssuanceService;
        this.operationLogService = operationLogService;
        this.systemSwitchService = systemSwitchService;
        this.captchaService = captchaService;
    }

    @Transactional
    public LoginResponseBody login(LoginRequest request, AuthRequestContext ctx) {
        String normalizedLoginName = request.loginName() == null ? "" : request.loginName().trim();

        loginAttemptService.ensureLoginAllowed(normalizedLoginName);
        verifyCaptchaIfRequired(request, normalizedLoginName, ctx);

        UserAccount user = userAccountRepository.findByLoginNameAndDeletedFlagFalse(normalizedLoginName)
                .orElseThrow(() -> invalidCredentials(normalizedLoginName, ctx));

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog("登录失败", user, normalizedLoginName, ctx, "失败", "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(normalizedLoginName, ctx);
        }

        if (Boolean.TRUE.equals(user.getTotpEnabled()) && user.getTotpSecret() != null) {
            String tempToken = generateTempToken(user.getId(), user.getCredentialVersion());
            return new LoginStep1Response(true, tempToken);
        }

        loginAttemptService.clearFailures(normalizedLoginName);
        user.setLastLoginDate(LocalDateTime.now());
        TokenResponse response = tokenIssuanceService.issueTokens(user, ctx.loginIp(), ctx.userAgent());
        recordLoginSuccess(user, ctx);
        return response;
    }

    private void verifyCaptchaIfRequired(LoginRequest request, String loginName, AuthRequestContext ctx) {
        if (systemSwitchService == null || !systemSwitchService.shouldRequireLoginCaptcha()) {
            return;
        }
        if (captchaService == null || !captchaService.verify(request.captchaId(), request.captchaCode())) {
            recordAuthenticationLog("登录失败", null, loginName, ctx, "失败", "图形验证码错误或已过期");
            throw new BadCredentialsException("图形验证码错误或已过期");
        }
    }

    @Transactional
    public TokenResponse verifyTotpAndIssueTokens(String tempToken, String totpCode, AuthRequestContext ctx) {
        String redisKey = TEMP_TOKEN_PREFIX + tempToken;
        String storedChallenge = redisTemplate.opsForValue().get(redisKey);
        if (storedChallenge == null) {
            throw expiredTempToken(null, ctx);
        }

        redisTemplate.delete(redisKey);
        TempTokenChallenge challenge = parseTempTokenChallenge(storedChallenge)
                .orElseThrow(() -> expiredTempToken(null, ctx));
        redisTemplate.delete(TEMP_TOKEN_USER_INDEX_PREFIX + challenge.userId());

        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalseForUpdate(challenge.userId())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        if (normalizeCredentialVersion(user.getCredentialVersion()) != challenge.credentialVersion()) {
            throw expiredTempToken(user, ctx);
        }

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog("登录失败", user, user.getLoginName(), ctx, "失败", "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        String secret = totpService.decryptSecret(user.getTotpSecret());
        if (!totpService.verifyCode(secret, totpCode)) {
            loginAttemptService.recordFailure(user.getLoginName());
            recordAuthenticationLog("登录失败", user, user.getLoginName(), ctx, "失败", "验证码错误或已过期");
            throw new BadCredentialsException("验证码错误或已过期");
        }

        loginAttemptService.clearFailures(user.getLoginName());
        user.setLastLoginDate(LocalDateTime.now());
        TokenResponse response = tokenIssuanceService.issueTokens(user, ctx.loginIp(), ctx.userAgent());
        recordLoginSuccess(user, ctx);
        return response;
    }

    private String generateTempToken(Long userId, Long credentialVersion) {
        String tempToken = userId + "." + UUID.randomUUID();
        String userIndexKey = TEMP_TOKEN_USER_INDEX_PREFIX + userId;
        String previousToken = redisTemplate.opsForValue().get(userIndexKey);
        if (previousToken != null && !previousToken.isBlank()) {
            redisTemplate.delete(TEMP_TOKEN_PREFIX + previousToken);
        }
        String challengeValue = TEMP_TOKEN_VALUE_VERSION + ":" + userId + ":"
                + normalizeCredentialVersion(credentialVersion);
        redisTemplate.opsForValue().set(TEMP_TOKEN_PREFIX + tempToken, challengeValue, TEMP_TOKEN_TTL);
        redisTemplate.opsForValue().set(userIndexKey, tempToken, TEMP_TOKEN_TTL);
        return tempToken;
    }

    private Optional<TempTokenChallenge> parseTempTokenChallenge(String storedChallenge) {
        String[] parts = storedChallenge.split(":", -1);
        if (parts.length != 3 || !TEMP_TOKEN_VALUE_VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(parts[1]);
            long credentialVersion = Long.parseLong(parts[2]);
            if (userId <= 0 || credentialVersion < 0) {
                return Optional.empty();
            }
            return Optional.of(new TempTokenChallenge(userId, credentialVersion));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private BadCredentialsException expiredTempToken(UserAccount user, AuthRequestContext ctx) {
        String loginName = user == null ? null : user.getLoginName();
        recordAuthenticationLog("登录失败", user, loginName, ctx, "失败", "2FA验证已过期，请重新登录");
        return new BadCredentialsException("2FA验证已过期，请重新登录");
    }

    private long normalizeCredentialVersion(Long credentialVersion) {
        return credentialVersion == null ? 0L : credentialVersion;
    }

    private BadCredentialsException invalidCredentials(String loginName, AuthRequestContext ctx) {
        loginAttemptService.recordFailure(loginName);
        recordAuthenticationLog("登录失败", null, loginName, ctx, "失败", "账号或密码错误");
        return new BadCredentialsException("账号或密码错误");
    }

    void recordLoginSuccess(UserAccount user, AuthRequestContext ctx) {
        recordAuthenticationLog("登录", user, user == null ? null : user.getLoginName(), ctx, "成功", "登录成功");
    }

    void recordAuthenticationLog(String actionType,
                                 UserAccount user,
                                 String loginName,
                                 AuthRequestContext ctx,
                                 String resultStatus,
                                 String remark) {
        if (operationLogService == null || systemSwitchService == null || !systemSwitchService.shouldRecordAuthenticationOperationLogs()) {
            return;
        }
        operationLogService.record(new OperationLogCommand(
                "认证授权",
                actionType,
                loginName,
                ctx.requestMethod() == null || ctx.requestMethod().isBlank() ? "POST" : ctx.requestMethod(),
                resolveAuthenticationRequestPath(actionType, ctx.requestPath()),
                ctx.loginIp(),
                resultStatus,
                remark,
                null,
                null,
                user == null ? null : user.getId(),
                user == null ? null : user.getUserName(),
                loginName
        ));
    }

    private static String resolveAuthenticationRequestPath(String actionType, String requestPath) {
        if (requestPath != null && !requestPath.isBlank()) {
            return requestPath;
        }
        return "退出登录".equals(actionType) ? "/auth/logout" : "/auth/login";
    }
}
