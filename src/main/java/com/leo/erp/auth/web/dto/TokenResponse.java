package com.leo.erp.auth.web.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AuthUserResponse user
) implements LoginResponseBody {
    @Override
    public String refreshTokenForCookie() {
        return refreshToken;
    }

    @Override
    public LoginResponseBody withoutSensitiveTokens() {
        return withoutRefreshToken();
    }

    public TokenResponse withoutRefreshToken() {
        return new TokenResponse(accessToken, null, tokenType, expiresIn, user);
    }
}
