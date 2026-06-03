package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AuthRefreshResult;
import com.leo.erp.auth.service.AuthSessionWebService;
import com.leo.erp.auth.web.dto.CaptchaResponse;
import com.leo.erp.auth.web.dto.Login2faRequest;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.LoginStep1Response;
import com.leo.erp.auth.web.dto.LogoutRequest;
import com.leo.erp.auth.web.dto.RefreshTokenRequest;
import com.leo.erp.auth.web.dto.TokenResponse;
import com.leo.erp.auth.web.support.AuthTokenCookieSupport;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final AuthSessionWebService authSessionWebService = mock(AuthSessionWebService.class);
    private final AuthTokenCookieSupport authTokenCookieSupport = mock(AuthTokenCookieSupport.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthController controller = new AuthController(
            authSessionWebService, authTokenCookieSupport, clientIpResolver, jwtTokenService
    );

    @Test
    void loginReturnsLoginResponseBody() {
        LoginRequest request = mock(LoginRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        TokenResponse body = new TokenResponse("access", "refresh", "Bearer", 3600L, 86400L, null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getRequestURI()).thenReturn("/auth/login");
        when(httpRequest.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(authSessionWebService.login(eq(request), any())).thenReturn(body);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(86400000L);

        ApiResponse<LoginResponseBody> response = controller.login(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("登录成功");
    }

    @Test
    void login2faReturnsTokenResponse() {
        Login2faRequest request = mock(Login2faRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        TokenResponse tokenResponse = new TokenResponse("access", "refresh", "Bearer", 3600L, 86400L, null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getRequestURI()).thenReturn("/auth/login-2fa");
        when(httpRequest.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(authSessionWebService.login2fa(eq(request), any())).thenReturn(tokenResponse);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(86400000L);

        ApiResponse<TokenResponse> response = controller.login2fa(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("登录成功");
    }

    @Test
    void refreshReturnsNewTokenWhenRefreshTokenValid() {
        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        TokenResponse tokenResponse = new TokenResponse("new-access", "new-refresh", "Bearer", 3600L, 86400L, null);
        AuthRefreshResult refreshResult = new AuthRefreshResult("刷新成功", tokenResponse);
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(authTokenCookieSupport.resolveRefreshToken(eq(httpRequest), any())).thenReturn("valid-token");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(authSessionWebService.refresh("valid-token", "127.0.0.1", "test-agent")).thenReturn(refreshResult);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(86400000L);

        ApiResponse<TokenResponse> response = controller.refresh(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("刷新成功");
    }

    @Test
    void refreshClearsCookieWhenTokenInvalid() {
        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        AuthRefreshResult refreshResult = new AuthRefreshResult("刷新失败", null);
        when(authTokenCookieSupport.resolveRefreshToken(eq(httpRequest), any())).thenReturn("invalid-token");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(authSessionWebService.refresh("invalid-token", "127.0.0.1", "test-agent")).thenReturn(refreshResult);

        ApiResponse<TokenResponse> response = controller.refresh(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("刷新失败");
        assertThat(response.data()).isNull();
        verify(authTokenCookieSupport).clearRefreshTokenCookie(httpResponse);
    }

    @Test
    void logoutClearsCookieAndReturnsSuccess() {
        LogoutRequest request = mock(LogoutRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        when(authTokenCookieSupport.resolveRefreshToken(eq(httpRequest), any())).thenReturn("token");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getRequestURI()).thenReturn("/auth/logout");
        when(httpRequest.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");

        ApiResponse<Void> response = controller.logout(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("退出成功");
        verify(authSessionWebService).logout(eq("token"), any());
        verify(authTokenCookieSupport).clearRefreshTokenCookie(httpResponse);
    }

    @Test
    void captchaReturnsCaptchaResponse() {
        CaptchaResponse captchaResponse = mock(CaptchaResponse.class);
        when(authSessionWebService.captcha()).thenReturn(captchaResponse);

        ApiResponse<CaptchaResponse> response = controller.captcha();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(captchaResponse);
    }

    @Test
    void pingReturnsPong() {
        ApiResponse<String> response = controller.ping();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo("pong");
        assertThat(response.message()).isEqualTo("认证模块可用");
    }

    @Test
    void loginWith2faRequiredDoesNotWriteCookie() {
        LoginRequest request = mock(LoginRequest.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginStep1Response step1Response = new LoginStep1Response(true, "temp-token");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getRequestURI()).thenReturn("/auth/login");
        when(httpRequest.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(authSessionWebService.login(eq(request), any())).thenReturn(step1Response);

        ApiResponse<LoginResponseBody> response = controller.login(request, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(step1Response);
    }

    @Test
    void refreshWithNullRequestResolvesTokenFromCookieOnly() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        TokenResponse tokenResponse = new TokenResponse("new-access", "new-refresh", "Bearer", 3600L, 86400L, null);
        AuthRefreshResult refreshResult = new AuthRefreshResult("刷新成功", tokenResponse);
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(authTokenCookieSupport.resolveRefreshToken(eq(httpRequest), eq(null))).thenReturn("cookie-token");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");
        when(authSessionWebService.refresh("cookie-token", "127.0.0.1", "test-agent")).thenReturn(refreshResult);
        when(jwtTokenService.getRefreshExpirationMs()).thenReturn(86400000L);

        ApiResponse<TokenResponse> response = controller.refresh(null, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("刷新成功");
    }

    @Test
    void logoutWithNullRequestResolvesTokenFromCookieOnly() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        when(authTokenCookieSupport.resolveRefreshToken(eq(httpRequest), eq(null))).thenReturn("cookie-token");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpRequest.getRequestURI()).thenReturn("/auth/logout");
        when(httpRequest.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolveClientIpOrUnknown(httpRequest)).thenReturn("127.0.0.1");

        ApiResponse<Void> response = controller.logout(null, httpRequest, httpResponse);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("退出成功");
        verify(authSessionWebService).logout(eq("cookie-token"), any());
        verify(authTokenCookieSupport).clearRefreshTokenCookie(httpResponse);
    }
}