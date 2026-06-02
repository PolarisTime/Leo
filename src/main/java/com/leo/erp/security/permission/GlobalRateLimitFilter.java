package com.leo.erp.security.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.RateLimitContext;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Global IP-level rate limiting — first line of defense.
 * Rejects at 429 before any business logic runs.
 * Skips: health check, static resources, public endpoints.
 */
@Component
public class GlobalRateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final double GLOBAL_RATE = 100;   // tokens/sec
    private static final int GLOBAL_CAPACITY = 150;  // burst

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/health", "/api/auth/ping", "/api/auth/captcha",
            "/api/setup/status", "/api/meta"
    );

    private final TokenBucketService tokenBucketService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public GlobalRateLimitFilter(TokenBucketService tokenBucketService,
                                 ClientIpResolver clientIpResolver,
                                 ObjectMapper objectMapper) {
        this.tokenBucketService = tokenBucketService;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String ip = clientIpResolver.resolveClientIpOrUnknown(request);
        String key = "global:ip:" + ip;

        TokenBucketService.TokenBucketResult result = tokenBucketService.tryConsume(
                key, GLOBAL_RATE, GLOBAL_CAPACITY, 1);

        if (!result.allowed()) {
            long retryAfterSeconds = result.retryAfterSeconds();
            RateLimitContext.Snapshot snapshot =
                    RateLimitContext.Snapshot.rejected(GLOBAL_CAPACITY, retryAfterSeconds);
            RateLimitContext.set(request, snapshot);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf(GLOBAL_CAPACITY));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(retryAfterSeconds));
            ApiResponse<Void> body = ApiResponse.failure(
                    ErrorCode.RATE_LIMITED,
                    "请求过于频繁，请在 " + retryAfterSeconds + " 秒后重试",
                    snapshot
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            response.getWriter().flush();
            return;
        }
        RateLimitContext.set(request, RateLimitContext.Snapshot.allowed(GLOBAL_CAPACITY, result.remaining()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(GLOBAL_CAPACITY));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        chain.doFilter(request, response);
    }
}
