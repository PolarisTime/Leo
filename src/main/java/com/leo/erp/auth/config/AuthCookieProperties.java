package com.leo.erp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.auth.cookie")
public record AuthCookieProperties(
        String refreshTokenName,
        String refreshTokenPath,
        boolean secure,
        String sameSite
) {
}
