package com.leo.erp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 读路径限流过滤器行为测试（2026-09-29 压测报告建议第 3 条的回归保护）。
 *
 * <p>核心断言：</p>
 * <ol>
 *   <li>默认关闭时零影响：任何请求都不触达计数组件（现有测试全绿即证明行为一致）；</li>
 *   <li>开启后额度内放行、超额 429 + Retry-After，且 body 符合项目 RFC 9457 契约
 *       （4290 / urn:leo:problem:too-many-requests，与 GlobalExceptionHandler 输出同构）；</li>
 *   <li>双维度分别生效、匿名按 IP 计键、不同用户互不影响；</li>
 *   <li><b>fail-open</b>：限流后端抛异常或返回不可用时放行且不产生 5xx
 *       （限流器绝不允许成为 500 来源）；</li>
 *   <li>白名单（health / actuator / 登录）永不受限——登录被限流会把登录风暴防护变成拒绝服务。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-29T08:00:00Z");

    @Mock
    private RateLimitStore store;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        // 除「默认关闭」专项外，其余用例均在开启状态下验证行为
        properties.setEnabled(true);
        filter = new RateLimitFilter(
                properties,
                store,
                new ApiErrorResponseWriter(
                        Jackson2ObjectMapperBuilder.json().build(),
                        new ApiProblemFactory("Asia/Shanghai")),
                new ClientIpResolver(""),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
        );
        when(store.tryAcquire(any(RateLimitCheck.class))).thenReturn(RateLimitResult.allowed());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- 关闭状态：零影响 -----------------------------------------------------------

    @Test
    void disabledByDefault_neverTouchesRateLimitStore() throws Exception {
        properties.setEnabled(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        verify(store, never()).tryAcquire(any(RateLimitCheck.class));
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ---- 额度内放行 / 429 契约 ------------------------------------------------------

    @Test
    void withinQuota_passesThroughAndBuildsCheckFromProperties() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<RateLimitCheck> captor = ArgumentCaptor.forClass(RateLimitCheck.class);
        verify(store).tryAcquire(captor.capture());
        RateLimitCheck check = captor.getValue();
        assertThat(check.subjectLimit()).isEqualTo(60);
        assertThat(check.subjectWindow()).isEqualTo(Duration.ofSeconds(1));
        assertThat(check.globalLimit()).isEqualTo(300);
        assertThat(check.globalWindow()).isEqualTo(Duration.ofSeconds(1));
        assertThat(check.now()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void subjectOverQuota_returns429WithRetryAfterAndProblemDetail() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class)))
                .thenReturn(RateLimitResult.limited(RateLimitResult.Dimension.SUBJECT, 1500L));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        // Retry-After 语义为整秒且向上取整：1500ms → 2s
        assertThat(response.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("2");
        assertThat(response.getContentType()).contains("application/problem+json");

        String body = response.getContentAsString();
        assertThat(body).contains("\"type\":\"urn:leo:problem:too-many-requests\"");
        assertThat(body).contains("\"title\":\"请求过于频繁，请稍后重试\"");
        assertThat(body).contains("\"status\":429");
        assertThat(body).contains("\"detail\":\"请求过于频繁，请稍后重试\"");
        assertThat(body).contains("\"instance\":\"/api/v2.0/sales-orders\"");
        // 错误码必须是 4290（TOO_MANY_REQUESTS），与 ExportConcurrencyGuard 同码先例
        assertThat(body).contains("\"code\":4290");
        assertThat(body).contains("\"timestamp\":");
    }

    @Test
    void globalOverQuota_returns429WithRoundedRetryAfter() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class)))
                .thenReturn(RateLimitResult.limited(RateLimitResult.Dimension.GLOBAL, 500L));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("\"code\":4290");
    }

    @Test
    void retryAfter_roundsUpAndNeverBelowOneSecond() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class)))
                .thenReturn(RateLimitResult.limited(RateLimitResult.Dimension.GLOBAL, 1L));
        MockHttpServletResponse tiny = new MockHttpServletResponse();
        filter.doFilter(get("/api/v2.0/sales-orders"), tiny, (req, res) -> { });
        assertThat(tiny.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("1");

        when(store.tryAcquire(any(RateLimitCheck.class)))
                .thenReturn(RateLimitResult.limited(RateLimitResult.Dimension.GLOBAL, 1001L));
        MockHttpServletResponse slightlyOver = new MockHttpServletResponse();
        filter.doFilter(get("/api/v2.0/sales-orders"), slightlyOver, (req, res) -> { });
        assertThat(slightlyOver.getHeader(RateLimitFilter.RETRY_AFTER_HEADER)).isEqualTo("2");
    }

    // ---- 双维度 / 计键 ---------------------------------------------------------------

    @Test
    void authenticatedRequest_keyedByUserIdentity() throws Exception {
        authenticate(42L, "perf_user");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> { });

        assertThat(subjectOfCall()).isEqualTo("u:42");
    }

    @Test
    void anonymousRequest_keyedByClientIp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        MockHttpServletRequest request = get("/api/v2.0/sales-orders");
        request.setRemoteAddr("10.1.2.3");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(subjectOfCall()).isEqualTo("ip:10.1.2.3");
    }

    @Test
    void unauthenticatedRequestWithoutSecurityContext_keyedByClientIp() throws Exception {
        MockHttpServletRequest request = get("/api/v2.0/sales-orders");
        request.setRemoteAddr("10.9.9.9");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(subjectOfCall()).isEqualTo("ip:10.9.9.9");
    }

    @Test
    void differentUsers_getIndependentSubjectKeys() throws Exception {
        authenticate(42L, "user_a");
        filter.doFilter(get("/api/v2.0/sales-orders"), new MockHttpServletResponse(), (req, res) -> { });

        SecurityContextHolder.clearContext();
        authenticate(43L, "user_b");
        filter.doFilter(get("/api/v2.0/sales-orders"), new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<RateLimitCheck> captor = ArgumentCaptor.forClass(RateLimitCheck.class);
        verify(store, times(2)).tryAcquire(captor.capture());
        String first = captor.getAllValues().get(0).subject();
        String second = captor.getAllValues().get(1).subject();
        assertThat(first).isEqualTo("u:42");
        assertThat(second).isEqualTo("u:43");
        assertThat(first).isNotEqualTo(second);
    }

    // ---- 白名单 ---------------------------------------------------------------------

    @Test
    void builtInWhitelistedPaths_bypassRateLimit() throws Exception {
        for (String uri : List.of(
                "/api/v2.0/health",
                "/api/v2.0/health/",          // 结尾斜杠同样豁免
                "/api/v2.0/system/health",
                "/api/actuator",
                "/api/actuator/health",
                "/api/error")) {
            filter.doFilter(get(uri), new MockHttpServletResponse(), (req, res) -> { });
        }
        verify(store, never()).tryAcquire(any(RateLimitCheck.class));
    }

    @Test
    void loginAlwaysExempt_evenWhenWriteMethodsAreLimited() throws Exception {
        properties.setLimitWriteMethods(true);

        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v2.0/auth/login"), loginResponse, (req, res) -> { });
        assertThat(loginResponse.getStatus()).isEqualTo(200);

        // 对照组：写方法限流打开后，非白名单写接口必须被限流
        filter.doFilter(post("/api/v2.0/customers"), new MockHttpServletResponse(), (req, res) -> { });
        verify(store).tryAcquire(any(RateLimitCheck.class));
    }

    @Test
    void customExcludePaths_bypassRateLimit() throws Exception {
        properties.setExcludePaths(" /v2.0/public/** , /legacy ");

        filter.doFilter(get("/api/v2.0/public/banner"), new MockHttpServletResponse(), (req, res) -> { });
        filter.doFilter(get("/api/legacy"), new MockHttpServletResponse(), (req, res) -> { });
        verify(store, never()).tryAcquire(any(RateLimitCheck.class));

        filter.doFilter(get("/api/v2.0/sales-orders"), new MockHttpServletResponse(), (req, res) -> { });
        verify(store).tryAcquire(any(RateLimitCheck.class));
    }

    // ---- 方法范围 ---------------------------------------------------------------------

    @Test
    void writeMethodsNotLimitedByDefault() throws Exception {
        filter.doFilter(post("/api/v2.0/customers"), new MockHttpServletResponse(), (req, res) -> { });
        verify(store, never()).tryAcquire(any(RateLimitCheck.class));

        // 打开开关后同一请求被纳入限流（过滤器按请求实时读取配置）
        properties.setLimitWriteMethods(true);
        filter.doFilter(post("/api/v2.0/customers"), new MockHttpServletResponse(), (req, res) -> { });
        verify(store).tryAcquire(any(RateLimitCheck.class));
    }

    @Test
    void headAndOptions_neverLimited() throws Exception {
        properties.setLimitWriteMethods(true);

        MockHttpServletRequest head = get("/api/v2.0/sales-orders");
        head.setMethod("HEAD");
        filter.doFilter(head, new MockHttpServletResponse(), (req, res) -> { });

        MockHttpServletRequest options = get("/api/v2.0/sales-orders");
        options.setMethod("OPTIONS");
        filter.doFilter(options, new MockHttpServletResponse(), (req, res) -> { });

        verify(store, never()).tryAcquire(any(RateLimitCheck.class));
    }

    // ---- fail-open：限流器绝不产生 5xx ------------------------------------------------

    @Test
    void storeUnavailable_failOpenPassesThroughWithout5xx() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class))).thenReturn(RateLimitResult.unavailable());

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void storeThrows_failOpenPassesThroughWithout5xx() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class)))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        // 关键：异常不得穿透成 500，必须放行给后续链路
        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void nullStoreResult_treatedAsUnavailableAndPassesThrough() throws Exception {
        when(store.tryAcquire(any(RateLimitCheck.class))).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ---- 额度边界 --------------------------------------------------------------------

    @Test
    void nonPositiveLimits_bothDimensionsDisabledWithoutStoreAccess() throws Exception {
        properties.setUserLimit(0);
        properties.setGlobalLimit(-1);

        filter.doFilter(get("/api/v2.0/sales-orders"), new MockHttpServletResponse(), (req, res) -> { });

        verify(store, never()).tryAcquire(any(RateLimitCheck.class));
    }

    @Test
    void subjectLimitZero_globalStillEnforced() throws Exception {
        properties.setUserLimit(0);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(get("/api/v2.0/sales-orders"), response, (req, res) -> { });

        ArgumentCaptor<RateLimitCheck> captor = ArgumentCaptor.forClass(RateLimitCheck.class);
        verify(store).tryAcquire(captor.capture());
        // 主体额度 0 表示「关闭该维度」，由脚本直接判通过；全局维度照常计数
        assertThat(captor.getValue().subjectLimit()).isZero();
        assertThat(captor.getValue().globalLimit()).isEqualTo(300);
    }

    @Test
    void nonPositiveWindow_fallsBackToOneSecond() throws Exception {
        properties.setUserWindowSeconds(0);
        properties.setGlobalWindowSeconds(-5);

        filter.doFilter(get("/api/v2.0/sales-orders"), new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<RateLimitCheck> captor = ArgumentCaptor.forClass(RateLimitCheck.class);
        verify(store).tryAcquire(captor.capture());
        assertThat(captor.getValue().subjectWindow()).isEqualTo(Duration.ofSeconds(1));
        assertThat(captor.getValue().globalWindow()).isEqualTo(Duration.ofSeconds(1));
    }

    // ---- helpers ----------------------------------------------------------------------

    private void authenticate(Long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                SecurityPrincipal.authenticated(userId, username, 0L), null, List.of()));
    }

    private String subjectOfCall() {
        ArgumentCaptor<RateLimitCheck> captor = ArgumentCaptor.forClass(RateLimitCheck.class);
        verify(store).tryAcquire(captor.capture());
        return captor.getValue().subject();
    }

    private MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/api");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContextPath("/api");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
