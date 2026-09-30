package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.security.config.AuthFallbackProperties;
import com.leo.erp.security.permission.AuthorityProvider;
import com.leo.erp.security.support.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * JWT 认证过滤器在「依赖不可用」时的行为测试（2026-09-30 报告第 4 项 P0 的回归保护）。
 *
 * <p>核心断言：Redis 不可用时返回 <b>503</b>（不是 500、也不是 401），并带 {@code Retry-After}。
 * 实测原实现会把 {@code RedisConnectionFailureException} 穿透成 500，
 * 于是 Redis 抖动 = 所有已登录请求 500，且与代码缺陷同码，监控无法区分。</p>
 *
 * <p>同时覆盖两个反例：令牌本身坏掉仍是 401（不能变成 503），
 * 以及关闭降级窗口时不得「跳过吊销校验偷偷放行」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterInfrastructureTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuthenticatedUserCacheService authenticatedUserCacheService;

    @Mock
    private AccessTokenBlacklistService blacklistService;

    @Mock
    private SessionActivityService sessionActivityService;

    @Mock
    private AuthorityProvider authorityProvider;

    @Mock
    private Claims claims;

    private AuthFallbackProperties fallbackProperties;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        fallbackProperties = new AuthFallbackProperties();
        filter = new JwtAuthenticationFilter(
                jwtTokenService,
                authenticatedUserCacheService,
                blacklistService,
                sessionActivityService,
                new ApiErrorResponseWriter(new ObjectMapper(), new ApiProblemFactory("Asia/Shanghai")),
                authorityProvider,
                fallbackProperties
        );
        when(claims.get("uid")).thenReturn(42L);
        when(claims.get("sid")).thenReturn("session-1");
        when(claims.get("cv")).thenReturn(0L);
        when(jwtTokenService.parseAccessToken(anyString())).thenReturn(claims);
        when(authorityProvider.authoritiesFor(any(SecurityPrincipal.class))).thenReturn(List.of("roles:read"));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2.0/sales-orders");
        request.addHeader("Authorization", "Bearer token-value");
        return request;
    }

    @Test
    void redisDownAndFallbackDisabled_returns503NotInternalError() throws Exception {
        when(blacklistService.isSessionBlacklisted(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        // 关键：错误码必须是 5030，而不是通用 5000，否则监控无法区分依赖故障与代码缺陷
        assertThat(response.getContentAsString()).contains("\"code\":5030");
    }

    @Test
    void principalLookupInfrastructureFailure_returns503() throws Exception {
        when(blacklistService.isSessionBlacklisted(anyString())).thenReturn(false);
        when(blacklistService.isBlacklisted(anyLong())).thenReturn(false);
        when(authenticatedUserCacheService.getActivePrincipal(anyLong(), anyLong()))
                .thenThrow(new AuthenticationInfrastructureException(
                        "认证依赖（Redis）不可用", new DataAccessResourceFailureException("boom")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":5030");
    }

    @Test
    void redisDownButFallbackEnabled_usesSnapshotAndContinuesChain() throws Exception {
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(5);
        when(blacklistService.isSessionBlacklisted(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));
        when(authenticatedUserCacheService.getActivePrincipal(anyLong(), anyLong()))
                .thenReturn(Optional.of(SecurityPrincipal.authenticated(42L, "perf_user", 0L)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedSession_still401AndNever503() throws Exception {
        when(blacklistService.isSessionBlacklisted(anyString())).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":4010");
    }

    @Test
    void brokenToken_still401() throws Exception {
        when(jwtTokenService.parseAccessToken(anyString()))
                .thenThrow(new io.jsonwebtoken.MalformedJwtException("bad signature"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void sessionActivityFailure_doesNotBreakAuthenticatedRequest() throws Exception {
        when(blacklistService.isSessionBlacklisted(anyString())).thenReturn(false);
        when(blacklistService.isBlacklisted(anyLong())).thenReturn(false);
        when(authenticatedUserCacheService.getActivePrincipal(anyLong(), anyLong()))
                .thenReturn(Optional.of(SecurityPrincipal.authenticated(42L, "perf_user", 0L)));
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Redis down"))
                .when(sessionActivityService).touchSession(anyString());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(), response, chain);

        // 在线活跃时间戳是旁路统计，写失败不应把已经认证成功的请求打成 5xx
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        SecurityContextHolder.clearContext();
    }
}
