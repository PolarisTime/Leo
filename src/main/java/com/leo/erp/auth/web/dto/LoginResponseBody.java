package com.leo.erp.auth.web.dto;

public sealed interface LoginResponseBody permits LoginStep1Response, TokenResponse {

    default String refreshTokenForCookie() {
        return null;
    }

    default LoginResponseBody withoutSensitiveTokens() {
        return this;
    }
}
