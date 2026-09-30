package com.leo.erp.auth.service;

import com.leo.erp.system.operationlog.support.OperationLogConstants;
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
    private final LoginAuditService loginAuditService;

    public LoginService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttemptService,
            TokenIssuanceService tokenIssuanceService,
            OperationLogService operationLogService,
            LoginAuditService loginAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.operationLogService = operationLogService;
        this.loginAuditService = loginAuditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, AuthRequestContext ctx) {
        String normalizedLoginName = request.loginName() == null ? "" : request.loginName().trim();

        loginAttemptService.ensureLoginAllowed(normalizedLoginName);

        // 刻意**不加行锁**：登录事务里要跑密码哈希（bcrypt，百毫秒级），
        // 若在事务开头就锁住账号行，并发登录会在行锁上排队并长期占用连接，
        // 实测 20 并发即打满连接池（active 20/20、获取超时 19 次）并引发大面积超时。
        // 会话数上限的并发安全由 createSession 里对 auth_refresh_token 的加锁查询保证。
        UserAccount user = userAccountRepository.findByLoginNameAndDeletedFlagFalse(normalizedLoginName)
                .orElseThrow(() -> invalidCredentials(normalizedLoginName, ctx));

        if (user.getStatus() != UserStatus.NORMAL) {
            recordAuthenticationLog(OperationLogConstants.ACTION_LOGIN_FAILED, user, normalizedLoginName, ctx, OperationLogConstants.RESULT_FAILURE, "账户已禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已禁用");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(normalizedLoginName, ctx);
        }

        loginAttemptService.clearFailures(normalizedLoginName);
        // 必须走「原子 UPDATE + 节流」，不能写 user.setLastLoginDate(...)：
        // sys_user 带 @Version，原先每次登录都改实体，提交时做版本比对且冲突后不重试，
        // 实测同账号 20 并发登录仅 5% 成功（92% 返回 409），还会退化成登录风暴打满连接池。
        loginAuditService.recordSuccessfulLogin(user);
        TokenResponse response = tokenIssuanceService.issueTokens(user, ctx.loginIp(), ctx.userAgent());
        recordLoginSuccess(user, ctx);
        return response;
    }

    private BadCredentialsException invalidCredentials(String loginName, AuthRequestContext ctx) {
        loginAttemptService.recordFailure(loginName);
        recordAuthenticationLog(OperationLogConstants.ACTION_LOGIN_FAILED, null, loginName, ctx, OperationLogConstants.RESULT_FAILURE, "账号或密码错误");
        return new BadCredentialsException("账号或密码错误");
    }

    void recordLoginSuccess(UserAccount user, AuthRequestContext ctx) {
        recordAuthenticationLog(OperationLogConstants.ACTION_LOGIN, user, user == null ? null : user.getLoginName(), ctx, OperationLogConstants.RESULT_SUCCESS, "登录成功");
    }

    void recordAuthenticationLog(String actionType,
                                 UserAccount user,
                                 String loginName,
                                 AuthRequestContext ctx,
                                 String resultStatus,
                                 String remark) {
        operationLogService.record(new OperationLogCommand(
                "身份认证",
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
        return OperationLogConstants.ACTION_LOGOUT.equals(actionType)
                ? OperationLogConstants.PATH_LOGOUT
                : OperationLogConstants.PATH_LOGIN;
    }
}
