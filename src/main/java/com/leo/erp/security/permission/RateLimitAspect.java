package com.leo.erp.security.permission;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate-limit:";
    private static final double DEFAULT_RATE = 100;
    private static final int DEFAULT_CAPACITY = 150;

    private final TokenBucketService tokenBucketService;
    private final StringRedisTemplate redisTemplate;
    private final ClientIpResolver clientIpResolver;

    public RateLimitAspect(TokenBucketService tokenBucketService,
                           StringRedisTemplate redisTemplate,
                           ClientIpResolver clientIpResolver) {
        this.tokenBucketService = tokenBucketService;
        this.redisTemplate = redisTemplate;
        this.clientIpResolver = clientIpResolver;
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
                return reject(response, r);
            }
            setHeaders(response, r);
            return joinPoint.proceed();
        }

        // --- 维度2: Authenticated User ---
        String userId = resolveUserId(request);
        if (userId != null) {
            String key = "user:" + userId + ":" + joinPoint.getSignature().toShortString();
            TokenBucketService.TokenBucketResult r = tokenBucketService.tryConsume(
                    key, resolveRate(rateLimit), resolveCapacity(rateLimit), rateLimit.tokens());
            if (!r.allowed()) {
                return reject(response, r);
            }
            setHeaders(response, r);
            return joinPoint.proceed();
        }

        // --- 维度3: IP (legacy compatible) ---
        String clientIp = clientIpResolver.resolveClientIpOrUnknown(request);
        String key = "ip:" + clientIp + ":" + joinPoint.getSignature().toShortString();

        if (rateLimit.rate() <= 0) {
            // Legacy fixed-window fallback
            long windowSeconds = rateLimit.timeUnit().toSeconds(rateLimit.duration());
            String legacyKey = KEY_PREFIX + clientIp + ":" + joinPoint.getSignature().toShortString();
            Long count = redisTemplate.opsForValue().increment(legacyKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(legacyKey, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > rateLimit.maxRequests()) {
                log.warn("Rate limit exceeded (legacy): key={}", legacyKey);
                throw new BusinessException(ErrorCode.FORBIDDEN, "请求过于频繁，请稍后重试");
            }
            return joinPoint.proceed();
        }

        TokenBucketService.TokenBucketResult r = tokenBucketService.tryConsume(
                key, rateLimit.rate(), rateLimit.capacity(), rateLimit.tokens());
        if (!r.allowed()) {
            return reject(response, r);
        }
        setHeaders(response, r);
        return joinPoint.proceed();
    }

    private double resolveRate(RateLimit rl) {
        return rl.rate() > 0 ? rl.rate() : DEFAULT_RATE;
    }

    private int resolveCapacity(RateLimit rl) {
        return rl.capacity() > 0 ? rl.capacity() : DEFAULT_CAPACITY;
    }

    private Object reject(HttpServletResponse response, TokenBucketService.TokenBucketResult r) {
        if (response != null) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(r.retryAfterSeconds()));
            response.setHeader("X-RateLimit-Remaining", "0");
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "请求过于频繁，请在 " + r.retryAfterSeconds() + " 秒后重试");
    }

    private void setHeaders(HttpServletResponse response, TokenBucketService.TokenBucketResult r) {
        if (response != null) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        }
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
