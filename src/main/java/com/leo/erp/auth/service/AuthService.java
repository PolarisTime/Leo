package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.web.dto.CaptchaResponse;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final LoginService loginService;
    private final TokenIssuanceService tokenIssuanceService;
    private final SessionManagementService sessionManagementService;
    private final CaptchaService captchaService;
    private final SystemSwitchService systemSwitchService;

    public AuthService(LoginService loginService,
                       TokenIssuanceService tokenIssuanceService,
                       SessionManagementService sessionManagementService,
                       CaptchaService captchaService,
                       SystemSwitchService systemSwitchService) {
        this.loginService = loginService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.sessionManagementService = sessionManagementService;
        this.captchaService = captchaService;
        this.systemSwitchService = systemSwitchService;
    }

    public LoginResponseBody login(LoginRequest request, LoginService.AuthRequestContext ctx) {
        return loginService.login(request, ctx);
    }

    public TokenResponse verifyTotpAndIssueTokens(String tempToken, String totpCode, LoginService.AuthRequestContext ctx) {
        return loginService.verifyTotpAndIssueTokens(tempToken, totpCode, ctx);
    }

    public TokenResponse refresh(String refreshToken, String loginIp, String userAgent) {
        return tokenIssuanceService.refresh(refreshToken, loginIp, userAgent);
    }

    public void logout(String refreshToken, LoginService.AuthRequestContext ctx) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionManagementService.findActiveSession(refreshToken).ifPresent(session -> {
            UserAccount user = sessionManagementService.findUserById(session.getUserId());
            tokenIssuanceService.revokeSession(session);
            loginService.recordAuthenticationLog("退出登录", user, user == null ? null : user.getLoginName(), ctx, "成功", "退出成功");
        });
    }

    public CaptchaResponse captcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return new CaptchaResponse(
                result.captchaId(),
                result.captchaImage(),
                systemSwitchService.shouldRequireLoginCaptcha()
        );
    }
}
