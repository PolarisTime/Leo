package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LoginService {

    /** 认证请求上下文，封装重复出现的请求元数据 */
    public record AuthRequestContext(String loginIp, String userAgent, String requestPath, String requestMethod) {
    }

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TokenIssuanceService tokenIssuanceService;
    private final OperationLogService operationLogService;

    public LoginService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            TokenIssuanceService tokenIssuanceService,
            OperationLogService operationLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, AuthRequestContext ctx) {
        String normalizedLoginName = request.loginName() == null ? "" : request.loginName().trim();

        loginAttemptService.ensureLoginAllowed(normalizedLoginName);

        UserAccount user = userAccountRepository.findByLoginNameAndDeletedFlagFalse(normalizedLoginName)
                .orElseThrow(() -> invalidCredentials(normalizedLoginName, ctx));

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog("登录失败", user, normalizedLoginName, ctx, "失败", "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(normalizedLoginName, ctx);
        }

        loginAttemptService.clearFailures(normalizedLoginName);
        user.setLastLoginDate(LocalDateTime.now());
        TokenResponse response = tokenIssuanceService.issueTokens(user, ctx.loginIp(), ctx.userAgent());
        recordLoginSuccess(user, ctx);
        return response;
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
