package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AuthService;
import com.leo.erp.auth.service.AuthTokenCookieService;
import com.leo.erp.auth.web.dto.Login2faRequest;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.IpResolutionService;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenCookieService authTokenCookieService;
    private final JwtTokenService jwtTokenService;
    private final IpResolutionService ipResolutionService;

    public AuthController(AuthService authService,
                          AuthTokenCookieService authTokenCookieService,
                          JwtTokenService jwtTokenService,
                          IpResolutionService ipResolutionService) {
        this.authService = authService;
        this.authTokenCookieService = authTokenCookieService;
        this.jwtTokenService = jwtTokenService;
        this.ipResolutionService = ipResolutionService;
    }

    @PostMapping("/login")
    @RateLimit(maxRequests = 5, duration = 1, timeUnit = TimeUnit.MINUTES)
    public ApiResponse<LoginResponseBody> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        LoginResponseBody result = authService.login(
                request,
                resolveIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRequestURI(),
                httpRequest.getMethod()
        );
        return ApiResponse.success("登录成功", attachRefreshCookieIfNeeded(result, httpResponse));
    }

    @PostMapping("/login-2fa")
    public ApiResponse<TokenResponse> login2fa(@Valid @RequestBody Login2faRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        TokenResponse tokenResponse = authService.verifyTotpAndIssueTokens(
                request.tempToken(),
                request.totpCode(),
                resolveIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRequestURI(),
                httpRequest.getMethod()
        );
        authTokenCookieService.writeRefreshTokenCookie(
                httpResponse,
                tokenResponse.refreshToken(),
                java.time.Duration.ofMillis(jwtTokenService.getRefreshExpirationMs())
        );
        return ApiResponse.success("登录成功", tokenResponse.withoutRefreshToken());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String refreshToken = authTokenCookieService.resolveRefreshToken(httpRequest, request == null ? null : request.refreshToken());
        TokenResponse tokenResponse = authService.refresh(refreshToken, resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
        authTokenCookieService.writeRefreshTokenCookie(
                httpResponse,
                tokenResponse.refreshToken(),
                java.time.Duration.ofMillis(jwtTokenService.getRefreshExpirationMs())
        );
        return ApiResponse.success("刷新成功", tokenResponse.withoutRefreshToken());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        authService.logout(
                authTokenCookieService.resolveRefreshToken(httpRequest, request == null ? null : request.refreshToken()),
                resolveIp(httpRequest),
                httpRequest.getRequestURI(),
                httpRequest.getMethod()
        );
        authTokenCookieService.clearRefreshTokenCookie(httpResponse);
        return ApiResponse.success("退出成功", null);
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("认证模块可用", "pong");
    }

    private String resolveIp(HttpServletRequest request) {
        return ipResolutionService.resolveClientIpOrUnknown(request);
    }

    private LoginResponseBody attachRefreshCookieIfNeeded(LoginResponseBody result, HttpServletResponse response) {
        String refreshToken = result.refreshTokenForCookie();
        if (refreshToken == null || refreshToken.isBlank()) {
            return result;
        }
        authTokenCookieService.writeRefreshTokenCookie(
                response,
                refreshToken,
                java.time.Duration.ofMillis(jwtTokenService.getRefreshExpirationMs())
        );
        return result.withoutSensitiveTokens();
    }
}
