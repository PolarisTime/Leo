package com.leo.erp.common.ratelimit;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * 读路径限流过滤器：过载时快速返回 429 + {@code Retry-After}
 * （2026-09-29 压测报告建议第 3 条：避免延迟无界增长）。
 *
 * <h3>为什么放在认证之后</h3>
 * <p>{@link com.leo.erp.common.config.SecurityConfig} 里以
 * {@code addFilterAfter(rateLimitFilter, HttpIdempotencyFilter.class)} 接入安全过滤器链，
 * 即 {@code JwtAuthenticationFilter}（认证，写入 SecurityContext）→ {@code HttpIdempotencyFilter} →
 * 本过滤器，且早于授权过滤器（AuthorizationFilter）。这样判定时能拿到用户身份按用户计键，
 * 同时削峰发生在授权与数据库访问之前；拿不到身份（匿名/未认证）时退化为按客户端 IP 计键。
 * 幂等过滤器默认只处理写方法、本过滤器默认只处理 GET，两者互不重叠；
 * 若打开 {@code limit-write-methods}，也保持「先幂等、后限流」的确定顺序。</p>
 *
 * <h3>双维度与 429 契约</h3>
 * <p>全局 + 主体（用户/IP）双维度，任一超限即 429。响应通过 {@link ApiErrorResponseWriter}
 * 在过滤器内直写 RFC 9457 {@code ProblemDetail}（与 {@code HttpIdempotencyFilter} 同模式，
 * 不经过 {@code GlobalExceptionHandler}），错误码复用 {@link ErrorCode#TOO_MANY_REQUESTS}（4290，
 * 与 {@code ExportConcurrencyGuard} 同码先例），同时带 {@code Retry-After} 响应头。
 * 直写响应必须留结构化 WARN 日志（压测报告「幂等冲突静默无痕」的教训）。</p>
 *
 * <h3>白名单（登录被限流会把「登录风暴防护」变成拒绝服务）</h3>
 * <p>内置豁免（不可通过配置移除，防止误配把健康检查/登录打挂）：</p>
 * <ul>
 *   <li>{@code /v2.0/health} —— 探活接口，被限流会让负载均衡误判实例不健康；</li>
 *   <li>{@code /v2.0/system/health} —— 健康页；</li>
 *   <li>{@code /actuator}、{@code /actuator/**} —— 监控与探活端点；</li>
 *   <li>{@code /v2.0/auth/login} —— 登录接口：登录已有专门的失败次数保护
 *       （{@code leo.auth.login-protection}），若被读限流拦下，过载时所有人都无法登录；</li>
 *   <li>{@code /error} —— 错误分发入口，防御性豁免。</li>
 * </ul>
 * <p>额外豁免路径通过 {@code leo.rate-limit.exclude-paths}（Ant 通配，逗号分隔）追加。</p>
 *
 * <h3>默认关闭与 fail-open</h3>
 * <p>{@code leo.rate-limit.enabled=false}（默认）时第一行即放行，零额外开销、行为与现状完全一致。
 * 开启后：限流后端不可用（存储返回 UNAVAILABLE，或存储抛出任何异常——此处再兜一层 try/catch）
 * 一律放行 + WARN 日志，本过滤器绝不产生 5xx。</p>
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** 429 响应必须携带的重试提示头。 */
    static final String RETRY_AFTER_HEADER = "Retry-After";

    /** 429 提示文案，与 {@link ErrorCode#TOO_MANY_REQUESTS} 保持一致。 */
    static final String TOO_MANY_REQUESTS_MESSAGE = "请求过于频繁，请稍后重试";

    /** 毫秒/秒，用于 Retry-After 向上取整。 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 写方法集合，仅在 {@code limit-write-methods=true} 时纳入限流。 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * 内置豁免路径（Ant 风格）。理由见类注释；刻意不暴露为配置项，
     * 避免运维误删登录/探活豁免造成二次事故。
     */
    private static final List<String> BUILT_IN_EXCLUSIONS = List.of(
            "/v2.0/health",
            "/v2.0/system/health",
            "/actuator",
            "/actuator/**",
            "/v2.0/auth/login",
            "/error"
    );

    private final RateLimitProperties properties;
    private final RateLimitStore store;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties properties,
                           RateLimitStore store,
                           ApiErrorResponseWriter errorResponseWriter,
                           ClientIpResolver clientIpResolver,
                           Clock clock) {
        this.properties = properties;
        this.store = store;
        this.errorResponseWriter = errorResponseWriter;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 默认关闭：一次布尔判断即放行，不访问 Redis、不改变请求路径，行为与未接入限流完全一致。
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!isRateLimitedMethod(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = normalizedPath(request);
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 双维度额度都配置为 <=0：语义是「不限流」，直接短路，不产生任何 Redis 调用。
        if (properties.getGlobalLimit() <= 0 && properties.getUserLimit() <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitCheck check = new RateLimitCheck(
                subjectKey(request),
                properties.getUserLimit(),
                properties.userWindow(),
                properties.getGlobalLimit(),
                properties.globalWindow(),
                clock.instant()
        );
        RateLimitResult result;
        try {
            // 存储层契约是不抛异常，这里仍兜一层：限流器绝不允许成为 500 来源。
            result = store.tryAcquire(check);
        } catch (RuntimeException ex) {
            log.warn("限流组件异常，fail-open 放行: method={}, uri={}, subject={}, reason={}",
                    request.getMethod(), request.getRequestURI(), check.subject(), ex.toString());
            filterChain.doFilter(request, response);
            return;
        }
        if (result == null || result.status() == RateLimitResult.Status.UNAVAILABLE) {
            // Redis 故障 fail-open：放行 + WARN，绝不 5xx（限流器自身不能变成故障源）。
            log.warn("限流后端不可用，按 fail-open 放行: method={}, uri={}, subject={}",
                    request.getMethod(), request.getRequestURI(), check.subject());
            filterChain.doFilter(request, response);
            return;
        }
        if (result.status() == RateLimitResult.Status.ALLOWED) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = retryAfterSeconds(result.retryAfterMillis());
        // 直写 ProblemDetail 绕过全局异常处理器，必须留下结构化日志，否则 429 在链路上静默无痕。
        log.warn("读路径限流拒绝，返回 429: method={}, uri={}, subject={}, dimension={}, "
                        + "retryAfterSeconds={}, userLimit={}, globalLimit={}",
                request.getMethod(),
                request.getRequestURI(),
                check.subject(),
                result.dimension(),
                retryAfterSeconds,
                properties.getUserLimit(),
                properties.getGlobalLimit());
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.TOO_MANY_REQUESTS,
                TOO_MANY_REQUESTS_MESSAGE
        );
    }

    /** 默认仅限 GET；打开 {@code limit-write-methods} 后覆盖 POST/PUT/PATCH/DELETE。HEAD/OPTIONS 恒不限流。 */
    private boolean isRateLimitedMethod(String method) {
        if ("GET".equals(method)) {
            return true;
        }
        return properties.isLimitWriteMethods() && WRITE_METHODS.contains(method);
    }

    private boolean isExcluded(String path) {
        for (String pattern : BUILT_IN_EXCLUSIONS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        String configured = properties.getExcludePaths();
        if (configured == null || configured.isBlank()) {
            return false;
        }
        for (String pattern : configured.split(",")) {
            String trimmed = pattern.trim();
            if (!trimmed.isEmpty() && pathMatcher.match(trimmed, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 限流主体键：已认证请求按用户 ID（拿不到 SecurityPrincipal 时退回认证名），
     * 匿名或未认证请求按受信代理规则解析出的客户端 IP。
     */
    private String subjectKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof SecurityPrincipal securityPrincipal
                    && securityPrincipal.id() != null) {
                return "u:" + securityPrincipal.id();
            }
            String name = authentication.getName();
            if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) {
                return "u:" + name;
            }
        }
        return "ip:" + clientIpResolver.resolveClientIpOrUnknown(request);
    }

    /** 去掉 context-path（如 {@code /api}），与幂等过滤器的路径归一化保持一致。 */
    private String normalizedPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** Retry-After 语义为整秒：向上取整且至少 1 秒。 */
    private long retryAfterSeconds(long retryAfterMillis) {
        return Math.max(1L, (retryAfterMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND);
    }
}
