package com.leo.erp.security.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Test
    void shouldCreateWithConstructor() {
        JwtProperties properties = new JwtProperties("issuer", "secret", 1000L, 2000L);

        assertThat(properties.getIssuer()).isEqualTo("issuer");
        assertThat(properties.getSecret()).isEqualTo("secret");
        assertThat(properties.getAccessExpirationMs()).isEqualTo(1000L);
        assertThat(properties.getRefreshExpirationMs()).isEqualTo(2000L);
    }

    @Test
    void shouldCreateWithDefaultConstructor() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getIssuer()).isNull();
        assertThat(properties.getSecret()).isNull();
        assertThat(properties.getAccessExpirationMs()).isZero();
        assertThat(properties.getRefreshExpirationMs()).isZero();
    }

    @Test
    void shouldSetAndGetIssuer() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("leo-erp");

        assertThat(properties.getIssuer()).isEqualTo("leo-erp");
    }

    @Test
    void shouldSetAndGetSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("my-secret-key");

        assertThat(properties.getSecret()).isEqualTo("my-secret-key");
    }

    @Test
    void shouldSetAndGetAccessExpirationMs() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessExpirationMs(1800000L);

        assertThat(properties.getAccessExpirationMs()).isEqualTo(1800000L);
    }

    @Test
    void shouldSetAndGetRefreshExpirationMs() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshExpirationMs(604800000L);

        assertThat(properties.getRefreshExpirationMs()).isEqualTo(604800000L);
    }

    @Test
    void shouldSupportTypicalConfiguration() {
        JwtProperties properties = new JwtProperties(
                "leo-erp",
                "leo-erp-jwt-secret-key-2026-must-be-long-enough-for-hs512",
                1_800_000L,
                604_800_000L
        );

        assertThat(properties.getIssuer()).isEqualTo("leo-erp");
        assertThat(properties.getAccessExpirationMs()).isEqualTo(30 * 60 * 1000L);
        assertThat(properties.getRefreshExpirationMs()).isEqualTo(7 * 24 * 60 * 60 * 1000L);
    }
}
