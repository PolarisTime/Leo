package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.TokenResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final LoginService loginService;
    private final TokenIssuanceService tokenIssuanceService;
    private final SessionManagementService sessionManagementService;

    public AuthService(LoginService loginService,
                       TokenIssuanceService tokenIssuanceService,
                       SessionManagementService sessionManagementService) {
        this.loginService = loginService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.sessionManagementService = sessionManagementService;
    }

    public LoginResponseBody login(LoginRequest request, String loginIp, String userAgent, String requestPath, String requestMethod) {
        return loginService.login(request, loginIp, userAgent, requestPath, requestMethod);
    }

    public TokenResponse verifyTotpAndIssueTokens(String tempToken, String totpCode, String loginIp, String userAgent, String requestPath, String requestMethod) {
        return loginService.verifyTotpAndIssueTokens(tempToken, totpCode, loginIp, userAgent, requestPath, requestMethod);
    }

    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        return tokenIssuanceService.refresh(refreshToken, loginIp, userAgent);
    }

    public void logout(String refreshToken, String loginIp, String requestPath, String requestMethod) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionManagementService.findActiveSession(refreshToken).ifPresent(session -> {
            UserAccount user = sessionManagementService.findUserById(session.getUserId());
            tokenIssuanceService.revokeSession(session);
            loginService.recordAuthenticationLog("退出登录", user, user == null ? null : user.getLoginName(), loginIp, requestPath, requestMethod, "成功", "退出成功");
        });
    }
}
