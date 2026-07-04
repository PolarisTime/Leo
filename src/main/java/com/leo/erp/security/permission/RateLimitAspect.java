package com.leo.erp.security.permission;

import com.leo.erp.common.api.RateLimitContext;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.security.jwt.ApiKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final double DEFAULT_RATE = 100;
    private static final int DEFAULT_CAPACITY = 150;

    private final TokenBucketService tokenBucketService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitHeaderWriter headerWriter;

    public RateLimitAspect(TokenBucketService tokenBucketService,
                           ClientIpResolver clientIpResolver,
                           RateLimitHeaderWriter headerWriter) {
        this.tokenBucketService = tokenBucketService;
        this.clientIpResolver = clientIpResolver;
        this.headerWriter = headerWriter;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();

        // --- 维度1: API Key ---
        String apiKey = request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            String key = "apikey:" + Integer.toHexString(apiKey.hashCode());
            TokenBucketService.TokenBucketResult r = tokenBucketService.tryConsume(
                    key, resolveRate(rateLimit), resolveCapacity(rateLimit), rateLimit.tokens());
            if (!r.allowed()) {
                throw reject(response, r, rateLimit);
            }
            setRateLimitResult(request, response, r, rateLimit);
            return joinPoint.proceed();
        }

        // --- 维度2: Authenticated User ---
        String userId = resolveUserId(request);
        if (userId != null) {
            String key = "user:" + userId + ":" + joinPoint.getSignature().toShortString();
            TokenBucketService.TokenBucketResult r = tokenBucketService.tryConsume(
                    key, resolveRate(rateLimit), resolveCapacity(rateLimit), rateLimit.tokens());
            if (!r.allowed()) {
                throw reject(response, r, rateLimit);
            }
            setRateLimitResult(request, response, r, rateLimit);
            return joinPoint.proceed();
        }

        // --- 维度3: IP ---
        String clientIp = clientIpResolver.resolveClientIpOrUnknown(request);
        String key = "ip:" + clientIp + ":" + joinPoint.getSignature().toShortString();
        TokenBucketService.TokenBucketResult r = tokenBucketService.tryConsume(
                key, resolveRate(rateLimit), resolveCapacity(rateLimit), rateLimit.tokens());
        if (!r.allowed()) {
            throw reject(response, r, rateLimit);
        }
        setRateLimitResult(request, response, r, rateLimit);
        return joinPoint.proceed();
    }

    private double resolveRate(RateLimit rl) {
        return rl.rate() > 0 ? rl.rate() : DEFAULT_RATE;
    }

    private int resolveCapacity(RateLimit rl) {
        return rl.capacity() > 0 ? rl.capacity() : DEFAULT_CAPACITY;
    }

    private BusinessException reject(HttpServletResponse response, TokenBucketService.TokenBucketResult r, RateLimit rl) {
        HttpServletRequest request = currentRequest();
        long retryAfterSeconds = r.retryAfterSeconds();
        RateLimitContext.set(request, RateLimitContext.Snapshot.rejected(resolveCapacity(rl), retryAfterSeconds));
        if (response != null) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            headerWriter.writeRejected(response, resolveCapacity(rl), retryAfterSeconds);
        }
        return new BusinessException(ErrorCode.RATE_LIMITED,
                "请求过于频繁，请在 " + retryAfterSeconds + " 秒后重试");
    }

    private void setRateLimitResult(HttpServletRequest request,
                                    HttpServletResponse response,
                                    TokenBucketService.TokenBucketResult r,
                                    RateLimit rl) {
        RateLimitContext.set(request, RateLimitContext.Snapshot.allowed(resolveCapacity(rl), r.remaining()));
        headerWriter.writeAllowed(response, resolveCapacity(rl), r.remaining());
    }

    private String resolveUserId(HttpServletRequest request) {
        java.security.Principal auth = request.getUserPrincipal();
        if (auth != null) {
            return auth.getName();
        }
        return null;
    }

    private String normalizeKey(String value) {
        if (value == null) return "unknown";
        String trimmed = value.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private HttpServletRequest currentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletResponse currentResponse() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
        } catch (Exception e) {
            return null;
        }
    }
}
