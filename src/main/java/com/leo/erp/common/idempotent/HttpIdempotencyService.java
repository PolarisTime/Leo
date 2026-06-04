package com.leo.erp.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class HttpIdempotencyService {

    private static final String KEY_PREFIX = "http-idempotency:";
    private static final String PENDING_PREFIX = "PENDING:";
    private static final String COMPLETED_PREFIX = "COMPLETED:";

    private final StringRedisTemplate redisTemplate;

    public HttpIdempotencyService(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Decision start(String scopedKey, String fingerprint, Duration ttl) {
        if (redisTemplate == null) {
            return Decision.acquired();
        }

        String redisKey = redisKey(scopedKey);
        String pendingValue = PENDING_PREFIX + fingerprint;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, pendingValue, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return Decision.acquired();
            }

            String currentValue = redisTemplate.opsForValue().get(redisKey);
            if (currentValue == null) {
                return Decision.acquired();
            }
            if (currentValue.equals(pendingValue)) {
                return Decision.duplicatePending();
            }
            if (currentValue.equals(COMPLETED_PREFIX + fingerprint)) {
                return Decision.duplicateCompleted();
            }
            if (currentValue.startsWith(PENDING_PREFIX) || currentValue.startsWith(COMPLETED_PREFIX)) {
                return Decision.parameterMismatch();
            }
            return Decision.duplicatePending();
        } catch (RuntimeException ex) {
            log.warn(
                    "HTTP idempotency Redis unavailable, request will continue: key={}, reason={}",
                    scopedKey,
                    ex.getMessage()
            );
            return Decision.acquired();
        }
    }

    public void markCompleted(String scopedKey, String fingerprint, Duration ttl) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(redisKey(scopedKey), COMPLETED_PREFIX + fingerprint, ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to mark HTTP idempotency key completed: key={}, reason={}", scopedKey, ex.getMessage());
        }
    }

    public void release(String scopedKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(redisKey(scopedKey));
        } catch (RuntimeException ex) {
            log.warn("Failed to release HTTP idempotency key: key={}, reason={}", scopedKey, ex.getMessage());
        }
    }

    private String redisKey(String scopedKey) {
        return KEY_PREFIX + scopedKey;
    }

    public record Decision(Status status) {

        static Decision acquired() {
            return new Decision(Status.ACQUIRED);
        }

        static Decision duplicatePending() {
            return new Decision(Status.DUPLICATE_PENDING);
        }

        static Decision duplicateCompleted() {
            return new Decision(Status.DUPLICATE_COMPLETED);
        }

        static Decision parameterMismatch() {
            return new Decision(Status.PARAMETER_MISMATCH);
        }
    }

    public enum Status {
        ACQUIRED,
        DUPLICATE_PENDING,
        DUPLICATE_COMPLETED,
        PARAMETER_MISMATCH
    }
}
