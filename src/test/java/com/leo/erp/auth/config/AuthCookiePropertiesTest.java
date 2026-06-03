package com.leo.erp.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiePropertiesTest {

    @Test
    void shouldCreateRecordWithAllProperties() {
        AuthCookieProperties props = new AuthCookieProperties(
                "refresh_token",
                "/auth/refresh",
                true,
                "Lax"
        );

        assertThat(props.refreshTokenName()).isEqualTo("refresh_token");
        assertThat(props.refreshTokenPath()).isEqualTo("/auth/refresh");
        assertThat(props.secure()).isTrue();
        assertThat(props.sameSite()).isEqualTo("Lax");
    }

    @Test
    void shouldCreateRecordWithNullValues() {
        AuthCookieProperties props = new AuthCookieProperties(null, null, false, null);

        assertThat(props.refreshTokenName()).isNull();
        assertThat(props.refreshTokenPath()).isNull();
        assertThat(props.secure()).isFalse();
        assertThat(props.sameSite()).isNull();
    }

    @Test
    void shouldSupportEquality() {
        AuthCookieProperties props1 = new AuthCookieProperties("token", "/path", true, "Strict");
        AuthCookieProperties props2 = new AuthCookieProperties("token", "/path", true, "Strict");

        assertThat(props1).isEqualTo(props2);
        assertThat(props1.hashCode()).isEqualTo(props2.hashCode());
    }

    @Test
    void shouldSupportToString() {
        AuthCookieProperties props = new AuthCookieProperties("token", "/path", true, "Strict");

        assertThat(props.toString()).contains("token", "/path", "true", "Strict");
    }
}
