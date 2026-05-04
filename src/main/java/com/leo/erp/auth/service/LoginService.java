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
import java.util.UUID;

@Service
public class LoginService {

    private static final String TEMP_TOKEN_PREFIX = "auth:2fa:temp:";
    private static final String TEMP_TOKEN_USER_INDEX_PREFIX = "auth:2fa:user-temp:";
    private static final Duration TEMP_TOKEN_TTL = Duration.ofMinutes(5);

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
    public LoginResponseBody login(LoginRequest request, String loginIp, String userAgent, String requestPath, String requestMethod) {
        String normalizedLoginName = request.loginName() == null ? "" : request.loginName().trim();

        if (systemSwitchService.shouldRequireLoginCaptcha()
                && !captchaService.verify(request.captchaId(), request.captchaCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码错误或已过期");
        }

        loginAttemptService.ensureLoginAllowed(normalizedLoginName);

        UserAccount user = userAccountRepository.findByLoginNameAndDeletedFlagFalse(normalizedLoginName)
                .orElseThrow(() -> invalidCredentials(normalizedLoginName, loginIp, requestPath, requestMethod));

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog("登录失败", user, normalizedLoginName, loginIp, requestPath, requestMethod, "失败", "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(normalizedLoginName, loginIp, requestPath, requestMethod);
        }

        if (Boolean.TRUE.equals(user.getTotpEnabled()) && user.getTotpSecret() != null) {
            String tempToken = generateTempToken(user.getId());
            return new LoginStep1Response(true, tempToken);
        }

        loginAttemptService.clearFailures(normalizedLoginName);
        user.setLastLoginDate(LocalDateTime.now());
        TokenResponse response = tokenIssuanceService.issueTokens(user, loginIp, userAgent);
        recordLoginSuccess(user, loginIp, requestPath, requestMethod);
        return response;
    }

    @Transactional
    public TokenResponse verifyTotpAndIssueTokens(String tempToken, String totpCode, String loginIp, String userAgent, String requestPath, String requestMethod) {
        String redisKey = TEMP_TOKEN_PREFIX + tempToken;
        String userIdStr = redisTemplate.opsForValue().get(redisKey);
        if (userIdStr == null) {
            recordAuthenticationLog("登录失败", null, null, loginIp, requestPath, requestMethod, "失败", "2FA验证已过期，请重新登录");
            throw new BadCredentialsException("2FA验证已过期，请重新登录");
        }

        redisTemplate.delete(redisKey);
        redisTemplate.delete(TEMP_TOKEN_USER_INDEX_PREFIX + userIdStr);

        Long userId = Long.parseLong(userIdStr);
        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog("登录失败", user, user.getLoginName(), loginIp, requestPath, requestMethod, "失败", "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        String secret = totpService.decryptSecret(user.getTotpSecret());
        if (!totpService.verifyCode(secret, totpCode)) {
            loginAttemptService.recordFailure(user.getLoginName());
            recordAuthenticationLog("登录失败", user, user.getLoginName(), loginIp, requestPath, requestMethod, "失败", "验证码错误或已过期");
            throw new BadCredentialsException("验证码错误或已过期");
        }

        loginAttemptService.clearFailures(user.getLoginName());
        user.setLastLoginDate(LocalDateTime.now());
        TokenResponse response = tokenIssuanceService.issueTokens(user, loginIp, userAgent);
        recordLoginSuccess(user, loginIp, requestPath, requestMethod);
        return response;
    }

    private String generateTempToken(Long userId) {
        String tempToken = userId + "." + UUID.randomUUID();
        String userIndexKey = TEMP_TOKEN_USER_INDEX_PREFIX + userId;
        String previousToken = redisTemplate.opsForValue().get(userIndexKey);
        if (previousToken != null && !previousToken.isBlank()) {
            redisTemplate.delete(TEMP_TOKEN_PREFIX + previousToken);
        }
        redisTemplate.opsForValue().set(TEMP_TOKEN_PREFIX + tempToken, userId.toString(), TEMP_TOKEN_TTL);
        redisTemplate.opsForValue().set(userIndexKey, tempToken, TEMP_TOKEN_TTL);
        return tempToken;
    }

    private BadCredentialsException invalidCredentials(String loginName, String loginIp, String requestPath, String requestMethod) {
        loginAttemptService.recordFailure(loginName);
        recordAuthenticationLog("登录失败", null, loginName, loginIp, requestPath, requestMethod, "失败", "账号或密码错误");
        return new BadCredentialsException("账号或密码错误");
    }

    void recordLoginSuccess(UserAccount user, String loginIp, String requestPath, String requestMethod) {
        recordAuthenticationLog("登录", user, user == null ? null : user.getLoginName(), loginIp, requestPath, requestMethod, "成功", "登录成功");
    }

    void recordAuthenticationLog(String actionType,
                                 UserAccount user,
                                 String loginName,
                                 String loginIp,
                                 String requestPath,
                                 String requestMethod,
                                 String resultStatus,
                                 String remark) {
        if (operationLogService == null || systemSwitchService == null || !systemSwitchService.shouldRecordAuthenticationOperationLogs()) {
            return;
        }
        operationLogService.record(new OperationLogCommand(
                "认证授权",
                actionType,
                loginName,
                requestMethod == null || requestMethod.isBlank() ? "POST" : requestMethod,
                resolveAuthenticationRequestPath(actionType, requestPath),
                loginIp,
                resultStatus,
                remark,
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
