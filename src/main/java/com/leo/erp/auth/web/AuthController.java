package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AuthRefreshResult;
import com.leo.erp.auth.service.AuthSessionWebService;
import com.leo.erp.auth.service.LoginService;
import com.leo.erp.auth.web.support.AuthTokenCookieSupport;
import com.leo.erp.auth.web.dto.CaptchaResponse;
import com.leo.erp.auth.web.dto.Login2faRequest;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.permission.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {

    private final AuthSessionWebService authSessionWebService;
    private final AuthTokenCookieSupport authTokenCookieSupport;
    private final ClientIpResolver clientIpResolver;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthSessionWebService authSessionWebService,
                          AuthTokenCookieSupport authTokenCookieSupport,
                          ClientIpResolver clientIpResolver,
                          JwtTokenService jwtTokenService) {
        this.authSessionWebService = authSessionWebService;
        this.authTokenCookieSupport = authTokenCookieSupport;
        this.clientIpResolver = clientIpResolver;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    @RateLimit(maxRequests = 5, duration = 1, timeUnit = TimeUnit.MINUTES)
    public ApiResponse<LoginResponseBody> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        LoginResponseBody body = authSessionWebService.login(request, resolveAuthContext(httpRequest));
        return ApiResponse.success("登录成功", attachRefreshCookieIfNeeded(body, httpResponse));
    }

    @PostMapping("/login-2fa")
    @RateLimit(maxRequests = 5, duration = 1, timeUnit = TimeUnit.MINUTES)
    public ApiResponse<TokenResponse> login2fa(@Valid @RequestBody Login2faRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        TokenResponse tokenResponse = authSessionWebService.login2fa(request, resolveAuthContext(httpRequest));
        authTokenCookieSupport.writeRefreshTokenCookie(httpResponse, tokenResponse.refreshToken(), refreshTokenMaxAge());
        return ApiResponse.success("登录成功", tokenResponse.withoutRefreshToken());
    }

    @PostMapping("/refresh")
    @RateLimit(maxRequests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String refreshToken = authTokenCookieSupport.resolveRefreshToken(httpRequest, request == null ? null : request.refreshToken());
        AuthRefreshResult result = authSessionWebService.refresh(
                refreshToken,
                clientIpResolver.resolveClientIpOrUnknown(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
        if (result.token() == null) {
            authTokenCookieSupport.clearRefreshTokenCookie(httpResponse);
            return ApiResponse.success(result.message(), null);
        }
        authTokenCookieSupport.writeRefreshTokenCookie(httpResponse, result.token().refreshToken(), refreshTokenMaxAge());
        return ApiResponse.success(result.message(), result.token().withoutRefreshToken());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody(required = false) LogoutRequest request,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        authSessionWebService.logout(
                authTokenCookieSupport.resolveRefreshToken(httpRequest, request == null ? null : request.refreshToken()),
                resolveAuthContext(httpRequest)
        );
        authTokenCookieSupport.clearRefreshTokenCookie(httpResponse);
        return ApiResponse.success("退出成功");
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.success(authSessionWebService.captcha());
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("认证模块可用", "pong");
    }

    private LoginService.AuthRequestContext resolveAuthContext(HttpServletRequest request) {
        return new LoginService.AuthRequestContext(
                clientIpResolver.resolveClientIpOrUnknown(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod()
        );
    }

    private LoginResponseBody attachRefreshCookieIfNeeded(LoginResponseBody result, HttpServletResponse response) {
        String refreshToken = result.refreshTokenForCookie();
        if (refreshToken == null || refreshToken.isBlank()) {
            return result;
        }
        authTokenCookieSupport.writeRefreshTokenCookie(response, refreshToken, refreshTokenMaxAge());
        return result.withoutSensitiveTokens();
    }

    private Duration refreshTokenMaxAge() {
        return Duration.ofMillis(jwtTokenService.getRefreshExpirationMs());
    }
}
