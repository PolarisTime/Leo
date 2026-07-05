package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.repository.ApiKeyRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.idempotent.HttpIdempotencyFilter;
import com.leo.erp.common.idempotent.HttpIdempotencyService;
import com.leo.erp.security.jwt.ApiKeyAuthenticationFilter;
import com.leo.erp.security.jwt.ApiKeyUsageService;
import com.leo.erp.security.jwt.AccessTokenBlacklistService;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.jwt.ForceTotpSetupFilter;
import com.leo.erp.security.jwt.JwtAuthenticationFilter;
import com.leo.erp.security.jwt.JwtTokenService;
import com.leo.erp.security.jwt.SessionActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void shouldExposeTraceIdHeaderInCors() {
        WebSecurityProperties webSecurityProperties = new WebSecurityProperties();
        CorsConfigurationSource source = config.corsConfigurationSource(webSecurityProperties);

        assertThat(source.getCorsConfiguration(request()).getExposedHeaders())
                .contains(TraceIdFilter.TRACE_ID_HEADER);
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

    @Test
    void shouldPermitPublicHealthDocsOptionsAndPublicAccessMatcher() {
        securityContextRunner()
                .withPropertyValues(
                        "security-test.health-public=true",
                        "security-test.docs-public=true",
                        "security-test.hsts-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/version")).andExpect(status().isOk());
                    mockMvc.perform(get("/system/health")).andExpect(status().isOk());
                    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
                    mockMvc.perform(options("/secure")).andExpect(status().isOk());
                    mockMvc.perform(get("/public")).andExpect(status().isOk());
                    mockMvc.perform(get("/secure")).andExpect(status().isForbidden());
                    mockMvc.perform(get("/secure").with(user("operator")))
                            .andExpect(status().isOk());
                    mockMvc.perform(get("/secure").secure(true).with(user("operator")))
                            .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
                });
    }

    @Test
    void shouldRequireAuthenticationForPrivateHealthAndAdminForPrivateDocs() {
        securityContextRunner()
                .withPropertyValues(
                        "security-test.health-public=false",
                        "security-test.docs-public=false",
                        "security-test.hsts-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/version")).andExpect(status().isOk());
                    mockMvc.perform(get("/system/health")).andExpect(status().isForbidden());
                    mockMvc.perform(get("/system/health").with(user("operator")))
                            .andExpect(status().isOk());
                    mockMvc.perform(get("/swagger-ui/index.html").with(user("operator").roles("USER")))
                            .andExpect(status().isForbidden());
                    mockMvc.perform(get("/swagger-ui/index.html").with(user("admin").roles("ADMIN")))
                            .andExpect(status().isOk());
                    mockMvc.perform(get("/secure").secure(true).with(user("operator")))
                            .andExpect(header().doesNotExist("Strict-Transport-Security"));
                });
    }

    private WebApplicationContextRunner securityContextRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WebMvcAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        SecurityFilterAutoConfiguration.class
                ))
                .withUserConfiguration(SecurityTestConfiguration.class);
    }

    private MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SecurityConfig.class, SecurityTestController.class})
    static class SecurityTestConfiguration {

        @Bean
        PublicAccessRequestMatcher publicAccessRequestMatcher() {
            PublicAccessRequestMatcher matcher = mock(PublicAccessRequestMatcher.class);
            when(matcher.matches(any())).thenAnswer(invocation -> isPublicRequest(invocation.getArgument(
                    0,
                    jakarta.servlet.http.HttpServletRequest.class
            )));
            when(matcher.matcher(any())).thenAnswer(invocation -> {
                boolean matched = isPublicRequest(invocation.getArgument(
                        0,
                        jakarta.servlet.http.HttpServletRequest.class
                ));
                return matched
                        ? org.springframework.security.web.util.matcher.RequestMatcher.MatchResult.match()
                        : org.springframework.security.web.util.matcher.RequestMatcher.MatchResult.notMatch();
            });
            return matcher;
        }

        private boolean isPublicRequest(jakarta.servlet.http.HttpServletRequest request) {
            return "/public".equals(request.getRequestURI());
        }

        @Bean
        SurfaceAccessProperties surfaceAccessProperties(Environment environment) {
            SurfaceAccessProperties properties = new SurfaceAccessProperties();
            properties.getHealth().setPublicAccessEnabled(
                    environment.getProperty("security-test.health-public", Boolean.class, true)
            );
            properties.getDocs().setPublicAccessEnabled(
                    environment.getProperty("security-test.docs-public", Boolean.class, false)
            );
            return properties;
        }

        @Bean
        WebSecurityProperties webSecurityProperties(Environment environment) {
            WebSecurityProperties properties = new WebSecurityProperties();
            properties.getHeaders().setHstsEnabled(
                    environment.getProperty("security-test.hsts-enabled", Boolean.class, false)
            );
            return properties;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(ObjectMapper objectMapper) {
            return new JwtAuthenticationFilter(
                    mock(JwtTokenService.class),
                    mock(AuthenticatedUserCacheService.class),
                    mock(AccessTokenBlacklistService.class),
                    mock(SessionActivityService.class),
                    objectMapper
            );
        }

        @Bean
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ObjectMapper objectMapper) {
            return new ApiKeyAuthenticationFilter(
                    mock(ApiKeyRepository.class),
                    mock(UserAccountRepository.class),
                    objectMapper,
                    mock(UserRoleBindingService.class),
                    mock(ApiKeyUsageService.class)
            );
        }

        @Bean
        ForceTotpSetupFilter forceTotpSetupFilter(ObjectMapper objectMapper) {
            return new ForceTotpSetupFilter(objectMapper);
        }

        @Bean
        HttpIdempotencyFilter httpIdempotencyFilter(ObjectMapper objectMapper) {
            return new HttpIdempotencyFilter(mock(HttpIdempotencyService.class), objectMapper);
        }
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/system/health")
        String health() {
            return "ok";
        }

        @GetMapping("/swagger-ui/index.html")
        String docs() {
            return "docs";
        }

        @GetMapping("/public")
        String publicEndpoint() {
            return "public";
        }

        @GetMapping("/version")
        String version() {
            return "version";
        }

        @RequestMapping(path = "/secure", method = {RequestMethod.GET, RequestMethod.OPTIONS})
        String secure() {
            return "secure";
        }
    }
}
