package com.leo.erp.auth.service;

import com.leo.erp.auth.web.dto.CaptchaResponse;
import com.leo.erp.auth.web.dto.Login2faRequest;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.TokenResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionWebService {

    private final AuthService authService;

    public AuthSessionWebService(AuthService authService) {
        this.authService = authService;
    }

    public LoginResponseBody login(LoginRequest request, LoginService.AuthRequestContext ctx) {
        return authService.login(request, ctx);
    }

    public TokenResponse login2fa(Login2faRequest request, LoginService.AuthRequestContext ctx) {
        return authService.verifyTotpAndIssueTokens(request.tempToken(), request.totpCode(), ctx);
    }

    public AuthRefreshResult refresh(String refreshToken, String loginIp, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return new AuthRefreshResult("未登录", null);
        }
        TokenResponse tokenResponse = authService.refresh(refreshToken, loginIp, userAgent);
        return new AuthRefreshResult("刷新成功", tokenResponse);
    }

    public void logout(String refreshToken, LoginService.AuthRequestContext ctx) {
        authService.logout(refreshToken, ctx);
    }

    public CaptchaResponse captcha() {
        return authService.captcha();
    }
}
