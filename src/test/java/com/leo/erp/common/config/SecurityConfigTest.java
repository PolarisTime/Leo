package com.leo.erp.common.config;

import com.leo.erp.security.jwt.ApiKeyAuthenticationFilter;
import com.leo.erp.security.jwt.ForceTotpSetupFilter;
import com.leo.erp.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    @Test
    void shouldCreatePasswordEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();
        assertThat(encoder).isNotNull();
        assertThat(encoder.encode("test")).isNotEqualTo("test");
    }

    @Test
    void shouldCreateCorsConfigurationSource() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source).isNotNull();
        assertThat(source.getCorsConfiguration(request())).isNotNull();
        assertThat(source.getCorsConfiguration(request()).getAllowedOrigins())
                .contains("http://localhost:3100");
    }

    @Test
    void shouldAllowCredentialsInCors() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowCredentials()).isTrue();
    }

    @Test
    void shouldNormalizeListWithNullValues() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedOrigins(Arrays.asList("http://example.com", null, "", "http://test.com"));
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedOrigins())
                .containsExactly("http://example.com", "http://test.com");
    }

    @Test
    void shouldReturnEmptyList_whenAllowedOriginsIsNull() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedOrigins(null);
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedOrigins()).isEmpty();
    }

    @Test
    void shouldSetCorsMaxAge() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void shouldRegisterJwtAuthFilterAsDisabled() {
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
        FilterRegistrationBean<JwtAuthenticationFilter> registration = config.jwtAuthenticationFilterRegistration(jwtFilter);

        assertThat(registration).isNotNull();
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    void shouldRegisterApiKeyFilterAsDisabled() {
        ApiKeyAuthenticationFilter apiKeyFilter = mock(ApiKeyAuthenticationFilter.class);
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = config.apiKeyAuthenticationFilterRegistration(apiKeyFilter);

        assertThat(registration).isNotNull();
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    void shouldRegisterForceTotpFilterAsDisabled() {
        ForceTotpSetupFilter totpFilter = mock(ForceTotpSetupFilter.class);
        FilterRegistrationBean<ForceTotpSetupFilter> registration = config.forceTotpSetupFilterRegistration(totpFilter);

        assertThat(registration).isNotNull();
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    void shouldNormalizeAllowedMethods() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedMethods(Arrays.asList("GET", null, "POST", "  "));
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedMethods())
                .containsExactly("GET", "POST");
    }

    @Test
    void shouldNormalizeAllowedHeaders() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedHeaders(List.of("Authorization", "Content-Type"));
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type");
    }

    @Test
    void shouldReturnEmptyMethodsWhenNull() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedMethods(null);
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedMethods()).isEmpty();
    }

    @Test
    void shouldReturnEmptyHeadersWhenNull() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        webSecurityProperties.getCors().setAllowedHeaders(null);
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getAllowedHeaders()).isEmpty();
    }
}
