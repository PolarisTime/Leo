package com.leo.erp.common.config;

import com.leo.erp.security.jwt.ApiKeyAuthenticationFilter;
import com.leo.erp.security.jwt.ForceTotpSetupFilter;
import com.leo.erp.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] OPEN_API_PATHS = {
            "/api-docs",
            "/api-docs/**",
            "/doc",
            "/doc/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   PublicAccessRequestMatcher publicAccessRequestMatcher,
                                                   SurfaceAccessProperties surfaceAccessProperties,
                                                   WebSecurityProperties webSecurityProperties,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                   ForceTotpSetupFilter forceTotpSetupFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'"
                        ))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .xssProtection(xss -> xss.disable())
                        .httpStrictTransportSecurity(hsts -> {
                            if (webSecurityProperties.getHeaders().isHstsEnabled()) {
                                hsts.includeSubDomains(true).maxAgeInSeconds(31536000);
                            } else {
                                hsts.disable();
                            }
                        })
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(publicAccessRequestMatcher).permitAll();
                    authorize.requestMatchers("/auth/**", "/health", "/error").permitAll();
                    if (surfaceAccessProperties.getHealth().isPublicAccessEnabled()) {
                        authorize.requestMatchers("/system/health").permitAll();
                    } else {
                        authorize.requestMatchers("/system/health").authenticated();
                    }
                    if (surfaceAccessProperties.getDocs().isPublicAccessEnabled()) {
                        authorize.requestMatchers(OPEN_API_PATHS).permitAll();
                    } else {
                        authorize.requestMatchers(OPEN_API_PATHS).hasRole("ADMIN");
                    }
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(forceTotpSetupFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebSecurityProperties webSecurityProperties) {
        WebSecurityProperties.Cors cors = webSecurityProperties.getCors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(normalizeList(cors.getAllowedOrigins()));
        configuration.setAllowedMethods(normalizeList(cors.getAllowedMethods()));
        configuration.setAllowedHeaders(normalizeList(cors.getAllowedHeaders()));
        configuration.setAllowCredentials(cors.isAllowCredentials());
        configuration.setMaxAge(cors.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterRegistration(
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(apiKeyAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ForceTotpSetupFilter> forceTotpSetupFilterRegistration(
            ForceTotpSetupFilter forceTotpSetupFilter) {
        FilterRegistrationBean<ForceTotpSetupFilter> registration = new FilterRegistrationBean<>(forceTotpSetupFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
