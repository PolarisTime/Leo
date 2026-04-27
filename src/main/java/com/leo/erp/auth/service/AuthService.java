package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final LoginService loginService;
    private final TokenIssuanceService tokenIssuanceService;
    private final OperationLogService operationLogService;
    private final SystemSwitchService systemSwitchService;

    public AuthService(LoginService loginService,
                       TokenIssuanceService tokenIssuanceService,
                       OperationLogService operationLogService,
                       SystemSwitchService systemSwitchService) {
        this.loginService = loginService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.operationLogService = operationLogService;
        this.systemSwitchService = systemSwitchService;
    }

    @Transactional
    public LoginResponseBody login(LoginRequest request, String loginIp, String userAgent, String requestPath, String requestMethod) {
        return loginService.login(request, loginIp, userAgent, requestPath, requestMethod);
    }

    @Transactional
    public TokenResponse verifyTotpAndIssueTokens(String tempToken, String totpCode, String loginIp, String userAgent, String requestPath, String requestMethod) {
        return loginService.verifyTotpAndIssueTokens(tempToken, totpCode, loginIp, userAgent, requestPath, requestMethod);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        return tokenIssuanceService.refresh(refreshToken, loginIp, userAgent);
    }

    @Transactional
    public void logout(String refreshToken, String loginIp, String requestPath, String requestMethod) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        tokenIssuanceService.findActiveSession(refreshToken).ifPresent(session -> {
            UserAccount user = tokenIssuanceService.findUserById(session.getUserId());
            tokenIssuanceService.logout(refreshToken);
            loginService.recordAuthenticationLog("退出登录", user, user == null ? null : user.getLoginName(), loginIp, requestPath, requestMethod, "成功", "退出成功");
        });
    }
}
