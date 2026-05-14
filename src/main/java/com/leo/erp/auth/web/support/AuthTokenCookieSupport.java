package com.leo.erp.auth.web.support;

import com.leo.erp.auth.config.AuthCookieProperties;
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
        if (fallbackToken != null && !fallbackToken.isBlank()) {
            return fallbackToken;
        }
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
