package com.leo.erp.auth.web.support;

import com.leo.erp.auth.config.AuthCookieProperties;
import com.leo.erp.common.error.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenCookieSupportTest {

    @Mock
    private AuthCookieProperties cookieProperties;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthTokenCookieSupport authTokenCookieSupport;

    @Test
    void shouldWriteRefreshTokenCookie() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        when(cookieProperties.secure()).thenReturn(true);
        when(cookieProperties.sameSite()).thenReturn("Lax");
        when(cookieProperties.refreshTokenPath()).thenReturn("/auth");

        authTokenCookieSupport.writeRefreshTokenCookie(response, "test-token", Duration.ofHours(1));

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(anyString(), headerCaptor.capture());

        String cookieHeader = headerCaptor.getValue();
        assertThat(cookieHeader).contains("refresh_token=test-token");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("Secure");
        assertThat(cookieHeader).contains("SameSite=Lax");
        assertThat(cookieHeader).contains("Path=/auth");
    }

    @Test
    void shouldClearRefreshTokenCookie() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        when(cookieProperties.secure()).thenReturn(false);
        when(cookieProperties.sameSite()).thenReturn("Strict");
        when(cookieProperties.refreshTokenPath()).thenReturn("/");

        authTokenCookieSupport.clearRefreshTokenCookie(response);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(anyString(), headerCaptor.capture());

        String cookieHeader = headerCaptor.getValue();
        assertThat(cookieHeader).contains("refresh_token=");
        assertThat(cookieHeader).contains("Max-Age=0");
    }

    @Test
    void shouldResolveRefreshTokenFromFallbackWhenCookieMissing() {
        when(request.getCookies()).thenReturn(null);

        String result = authTokenCookieSupport.resolveRefreshToken(request, "fallback-token");
        assertThat(result).isEqualTo("fallback-token");
    }

    @Test
    void shouldResolveRefreshTokenFromCookieWhenFallbackMatches() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("refresh_token", "same-token");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        String result = authTokenCookieSupport.resolveRefreshToken(request, "same-token");
        assertThat(result).isEqualTo("same-token");
    }

    @Test
    void shouldRejectRefreshTokenWhenCookieAndFallbackConflict() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("refresh_token", "cookie-token");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        assertThatThrownBy(() -> authTokenCookieSupport.resolveRefreshToken(request, "body-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新令牌来源不一致");
    }

    @Test
    void shouldResolveRefreshTokenFromCookieWhenFallbackIsBlank() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("refresh_token", "cookie-token");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        String result = authTokenCookieSupport.resolveRefreshToken(request, "");
        assertThat(result).isEqualTo("cookie-token");
    }

    @Test
    void shouldResolveRefreshTokenFromCookieWhenFallbackIsNull() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("refresh_token", "cookie-token");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        String result = authTokenCookieSupport.resolveRefreshToken(request, null);
        assertThat(result).isEqualTo("cookie-token");
    }

    @Test
    void shouldReturnNullWhenNoCookies() {
        when(request.getCookies()).thenReturn(null);

        String result = authTokenCookieSupport.resolveRefreshToken(request, null);
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenCookieNotFound() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("other_cookie", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        String result = authTokenCookieSupport.resolveRefreshToken(request, null);
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenCookieValueIsBlank() {
        when(cookieProperties.refreshTokenName()).thenReturn("refresh_token");
        Cookie cookie = new Cookie("refresh_token", "");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        String result = authTokenCookieSupport.resolveRefreshToken(request, null);
        assertThat(result).isNull();
    }
}
