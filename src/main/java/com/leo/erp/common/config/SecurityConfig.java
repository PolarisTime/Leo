package com.leo.erp.common.config;

import com.leo.erp.security.jwt.JwtAuthenticationFilter;
import com.leo.erp.system.setup.web.InitialSetupTokenFilter;
import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.idempotent.HttpIdempotencyFilter;
import com.leo.erp.common.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
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
                                                   InitialSetupTokenFilter initialSetupTokenFilter,
                                                   HttpIdempotencyFilter httpIdempotencyFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   ApiErrorResponseWriter errorResponseWriter) throws Exception {
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
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorResponseWriter.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED,
                                "未登录或登录已失效"
                        ))
                        .accessDeniedHandler((request, response, exception) -> errorResponseWriter.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN,
                                "拒绝访问"
                        )))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(publicAccessRequestMatcher).permitAll();
                    authorize.requestMatchers("/error").permitAll();
                    if (surfaceAccessProperties.getHealth().isPublicAccessEnabled()) {
                        authorize.requestMatchers("/v2.0/system/health").permitAll();
                    } else {
                        authorize.requestMatchers("/v2.0/system/health").authenticated();
                    }
                    if (surfaceAccessProperties.getDocs().isPublicAccessEnabled()) {
                        authorize.requestMatchers(OPEN_API_PATHS).permitAll();
                    } else {
                        authorize.requestMatchers(OPEN_API_PATHS).authenticated();
                    }
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .addFilterAfter(initialSetupTokenFilter, CorsFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(httpIdempotencyFilter, JwtAuthenticationFilter.class)
                // 读路径限流必须在认证之后（能拿到用户身份计键），且位于授权过滤器之前（削峰先于授权/DB 访问）；
                // 锚定在 HttpIdempotencyFilter 之后，保证「认证 → 幂等 → 限流」的确定顺序。
                .addFilterAfter(rateLimitFilter, HttpIdempotencyFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebSecurityProperties webSecurityProperties) {
        WebSecurityProperties.Cors cors = webSecurityProperties.getCors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(normalizeList(cors.getAllowedOrigins()));
        configuration.setAllowedMethods(normalizeList(cors.getAllowedMethods()));
        configuration.setAllowedHeaders(normalizeList(cors.getAllowedHeaders()));
        configuration.setExposedHeaders(List.of(TraceIdFilter.TRACE_ID_HEADER, HttpHeaders.CONTENT_DISPOSITION));
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
    public FilterRegistrationBean<InitialSetupTokenFilter> initialSetupTokenFilterRegistration(
            InitialSetupTokenFilter initialSetupTokenFilter) {
        FilterRegistrationBean<InitialSetupTokenFilter> registration =
                new FilterRegistrationBean<>(initialSetupTokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HttpIdempotencyFilter> httpIdempotencyFilterRegistration(
            HttpIdempotencyFilter httpIdempotencyFilter) {
        FilterRegistrationBean<HttpIdempotencyFilter> registration = new FilterRegistrationBean<>(httpIdempotencyFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 限流过滤器只在安全过滤器链内执行（依赖 SecurityContext 中的用户身份），
     * 禁止 Servlet 容器再注册一次，否则匿名请求会被按 IP 预先计数一次。
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CacheControlShallowEtagFilter> cacheControlShallowEtagFilterRegistration() {
        FilterRegistrationBean<CacheControlShallowEtagFilter> registration =
                new FilterRegistrationBean<>(new CacheControlShallowEtagFilter());
        registration.setName("cacheControlShallowEtagFilter");
        registration.setUrlPatterns(List.of(ApiVersion.V2_PREFIX + "/*"));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
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
