package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSecurityPropertiesTest {

    private final WebSecurityProperties properties = new WebSecurityProperties();

    @Test
    void shouldHaveCorsSection() {
        assertThat(properties.getCors()).isNotNull();
    }

    @Test
    void shouldHaveHeadersSection() {
        assertThat(properties.getHeaders()).isNotNull();
    }

    @Test
    void shouldHaveDefaultAllowedOrigins() {
        assertThat(properties.getCors().getAllowedOrigins())
                .contains("http://localhost:3100", "http://127.0.0.1:3100");
    }

    @Test
    void shouldHaveDefaultAllowedMethods() {
        assertThat(properties.getCors().getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void shouldHaveDefaultAllowedHeaders() {
        assertThat(properties.getCors().getAllowedHeaders())
                .contains("Authorization", "Content-Type", "X-API-Key", "X-Setup-Token");
    }

    @Test
    void shouldHaveAllowCredentialsEnabledByDefault() {
        assertThat(properties.getCors().isAllowCredentials()).isTrue();
    }

    @Test
    void shouldHaveDefaultMaxAge() {
        assertThat(properties.getCors().getMaxAgeSeconds()).isEqualTo(3600);
    }

    @Test
    void shouldSetAllowedOrigins() {
        properties.getCors().setAllowedOrigins(List.of("https://example.com"));
        assertThat(properties.getCors().getAllowedOrigins()).containsExactly("https://example.com");
    }

    @Test
    void shouldSetAllowedMethods() {
        properties.getCors().setAllowedMethods(List.of("GET", "POST"));
        assertThat(properties.getCors().getAllowedMethods()).containsExactly("GET", "POST");
    }

    @Test
    void shouldSetAllowCredentials() {
        properties.getCors().setAllowCredentials(false);
        assertThat(properties.getCors().isAllowCredentials()).isFalse();
    }

    @Test
    void shouldSetMaxAge() {
        properties.getCors().setMaxAgeSeconds(7200);
        assertThat(properties.getCors().getMaxAgeSeconds()).isEqualTo(7200);
    }

    @Test
    void shouldHaveHstsDisabledByDefault() {
        assertThat(properties.getHeaders().isHstsEnabled()).isFalse();
    }

    @Test
    void shouldSetHsts() {
        properties.getHeaders().setHstsEnabled(true);
        assertThat(properties.getHeaders().isHstsEnabled()).isTrue();
    }
}
