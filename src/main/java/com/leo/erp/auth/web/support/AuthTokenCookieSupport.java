package com.leo.erp.auth.web.support;

import com.leo.erp.auth.config.AuthCookieProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthTokenCookieSupport {

    private final AuthCookieProperties cookieProperties;

    public AuthTokenCookieSupport(AuthCookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    public void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieProperties.refreshTokenName(), refreshToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.refreshTokenPath())
                .maxAge(maxAge)
                .build()
                .toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieProperties.refreshTokenName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.refreshTokenPath())
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }

    public String resolveRefreshToken(HttpServletRequest request, String fallbackToken) {
        String cookieToken = resolveCookieRefreshToken(request);
        if (cookieToken != null) {
            if (fallbackToken != null && !fallbackToken.isBlank() && !cookieToken.equals(fallbackToken)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "刷新令牌来源不一致，请重新登录");
            }
            return cookieToken;
        }
        return fallbackToken == null || fallbackToken.isBlank() ? null : fallbackToken;
    }

    private String resolveCookieRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieProperties.refreshTokenName().equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
