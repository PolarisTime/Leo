package com.leo.erp.auth.service;

import com.leo.erp.auth.web.dto.CaptchaResponse;
import com.leo.erp.auth.web.dto.Login2faRequest;
import com.leo.erp.auth.web.dto.LoginRequest;
import com.leo.erp.auth.web.dto.LoginResponseBody;
import com.leo.erp.auth.web.dto.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionWebServiceTest {

    private static final LoginService.AuthRequestContext CTX =
            new LoginService.AuthRequestContext("127.0.0.1", "JUnit", "/auth/login", "POST");

    @Test
    void loginShouldDelegateToAuthService() {
        AuthService authService = mock(AuthService.class);
        LoginRequest request = new LoginRequest("admin", "secret", null, null);
        TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 300, 1800, null);
        when(authService.login(request, CTX)).thenReturn(expected);

        AuthSessionWebService service = new AuthSessionWebService(authService);

        LoginResponseBody result = service.login(request, CTX);

        assertThat(result).isEqualTo(expected);
        verify(authService).login(request, CTX);
    }

    @Test
    void login2faShouldDelegateToAuthService() {
        AuthService authService = mock(AuthService.class);
        Login2faRequest request = new Login2faRequest("temp-token", "123456");
        TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 300, 1800, null);
        when(authService.verifyTotpAndIssueTokens("temp-token", "123456", CTX)).thenReturn(expected);

        AuthSessionWebService service = new AuthSessionWebService(authService);

        TokenResponse result = service.login2fa(request, CTX);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void refreshShouldReturnNotLoggedInWhenTokenBlank() {
        AuthSessionWebService service = new AuthSessionWebService(mock(AuthService.class));

        AuthRefreshResult result = service.refresh(null, "127.0.0.1", "JUnit");

        assertThat(result.message()).isEqualTo("未登录");
        assertThat(result.token()).isNull();
    }

    @Test
    void refreshShouldReturnNotLoggedInWhenTokenEmpty() {
        AuthSessionWebService service = new AuthSessionWebService(mock(AuthService.class));

        AuthRefreshResult result = service.refresh("", "127.0.0.1", "JUnit");

        assertThat(result.message()).isEqualTo("未登录");
        assertThat(result.token()).isNull();
    }

    @Test
    void refreshShouldReturnNotLoggedInWhenTokenWhitespace() {
        AuthSessionWebService service = new AuthSessionWebService(mock(AuthService.class));

        AuthRefreshResult result = service.refresh("   ", "127.0.0.1", "JUnit");

        assertThat(result.message()).isEqualTo("未登录");
        assertThat(result.token()).isNull();
    }

    @Test
    void refreshShouldDelegateWhenTokenPresent() {
        AuthService authService = mock(AuthService.class);
        TokenResponse tokenResponse = new TokenResponse("new-access", "new-refresh", "Bearer", 300, 1800, null);
        when(authService.refresh("refresh-token", "127.0.0.1", "JUnit")).thenReturn(tokenResponse);

        AuthSessionWebService service = new AuthSessionWebService(authService);

        AuthRefreshResult result = service.refresh("refresh-token", "127.0.0.1", "JUnit");

        assertThat(result.message()).isEqualTo("刷新成功");
        assertThat(result.token()).isEqualTo(tokenResponse);
    }

    @Test
    void logoutShouldDelegateToAuthService() {
        AuthService authService = mock(AuthService.class);
        AuthSessionWebService service = new AuthSessionWebService(authService);

        service.logout("refresh-token", CTX);

        verify(authService).logout("refresh-token", CTX);
    }

    @Test
    void captchaShouldDelegateToAuthService() {
        AuthService authService = mock(AuthService.class);
        CaptchaResponse expected = new CaptchaResponse("key", "image", true);
        when(authService.captcha()).thenReturn(expected);

        AuthSessionWebService service = new AuthSessionWebService(authService);

        assertThat(service.captcha()).isEqualTo(expected);
    }
}
