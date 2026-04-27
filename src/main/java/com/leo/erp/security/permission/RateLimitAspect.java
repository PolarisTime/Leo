package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.IpResolutionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);
    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;
    private final IpResolutionService ipResolutionService;

    public RateLimitAspect(StringRedisTemplate redisTemplate, IpResolutionService ipResolutionService) {
        this.redisTemplate = redisTemplate;
        this.ipResolutionService = ipResolutionService;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(joinPoint);
        long windowSeconds = rateLimit.timeUnit().toSeconds(rateLimit.duration());

        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            return joinPoint.proceed();
        }

        if (currentCount == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (currentCount > rateLimit.maxRequests()) {
            log.warn("Rate limit exceeded: key={}, limit={}/{}s", key, rateLimit.maxRequests(), windowSeconds);
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求过于频繁，请稍后重试");
        }

        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint) {
        String clientIp = resolveClientIp();
        String method = joinPoint.getSignature().toShortString();
        return KEY_PREFIX + clientIp + ":" + method;
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return ipResolutionService.resolveClientIpOrUnknown(attrs.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }
}
