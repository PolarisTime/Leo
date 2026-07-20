package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TokenResponse login(LoginRequest request, LoginService.AuthRequestContext ctx) {
        return loginService.login(request, ctx);
    }

    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        return tokenIssuanceService.refresh(refreshToken, loginIp, userAgent);
    }

    @Transactional
    public void logout(String refreshToken, LoginService.AuthRequestContext ctx) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionManagementService.findActiveSessionForLogout(refreshToken).ifPresent(session -> {
            UserAccount user = sessionManagementService.findUserById(session.getUserId());
            tokenIssuanceService.revokeSession(session);
            loginService.recordAuthenticationLog("退出登录", user, user == null ? null : user.getLoginName(), ctx, "成功", "退出成功");
        });
    }

}
