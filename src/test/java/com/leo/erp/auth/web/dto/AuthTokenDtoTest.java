package com.leo.erp.auth.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenDtoTest {

    @Test
    void shouldUseDefaultLoginResponseBodyMethodsForStep1Response() {
        LoginStep1Response response = new LoginStep1Response(true, "temp-token");

        assertThat(response.refreshTokenForCookie()).isNull();
        assertThat(response.withoutSensitiveTokens()).isSameAs(response);
    }

    @Test
    void shouldConstructRefreshTokenRequest() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        assertThat(request.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldConstructLogoutRequest() {
        LogoutRequest request = new LogoutRequest("refresh-token");

        assertThat(request.refreshToken()).isEqualTo("refresh-token");
    }
}
