package com.leo.erp.auth.web.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        AuthUserResponse user
) {
    public String refreshTokenForCookie() {
        return refreshToken;
    }

    public TokenResponse withoutRefreshToken() {
        return new TokenResponse(accessToken, null, tokenType, expiresIn, refreshExpiresIn, user);
    }
}
