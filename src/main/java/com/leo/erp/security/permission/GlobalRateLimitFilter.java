package com.leo.erp.security.permission;

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

    public GlobalRateLimitFilter(TokenBucketService tokenBucketService,
                                 ClientIpResolver clientIpResolver) {
        this.tokenBucketService = tokenBucketService;
        this.clientIpResolver = clientIpResolver;
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
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请在 " + result.retryAfterSeconds() + " 秒后重试\"}");
            return;
        }
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        chain.doFilter(request, response);
    }
}
