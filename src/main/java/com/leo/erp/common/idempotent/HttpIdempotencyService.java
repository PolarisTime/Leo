package com.leo.erp.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class HttpIdempotencyService {

    private static final String KEY_PREFIX = "http-idempotency:";
    private static final String PENDING_PREFIX = "PENDING:";
    private static final String COMPLETED_PREFIX = "COMPLETED:";
    private static final DefaultRedisScript<String> START_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if not current then
                redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[1])
                return 'ACQUIRED'
            end
            if current == ARGV[1] then
                return 'DUPLICATE_PENDING'
            end
            if current == ARGV[2] then
                return 'DUPLICATE_COMPLETED'
            end
            if string.sub(current, 1, string.len(ARGV[4])) == ARGV[4]
                or string.sub(current, 1, string.len(ARGV[5])) == ARGV[5] then
                return 'PARAMETER_MISMATCH'
            end
            return 'DUPLICATE_PENDING'
            """,
            String.class
    );
    private static final DefaultRedisScript<Long> MARK_COMPLETED_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
                return 1
            end
            return 0
            """,
            Long.class
    );
    private static final DefaultRedisScript<Long> RELEASE_PENDING_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            return 0
            """,
            Long.class
    );

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
        String completedValue = COMPLETED_PREFIX + fingerprint;
        try {
            String status = redisTemplate.execute(
                    START_SCRIPT,
                    List.of(redisKey),
                    pendingValue,
                    completedValue,
                    ttlMillis(ttl),
                    PENDING_PREFIX,
                    COMPLETED_PREFIX
            );
            return switch (status == null ? "" : status) {
                case "ACQUIRED" -> Decision.acquired();
                case "DUPLICATE_PENDING" -> Decision.duplicatePending();
                case "DUPLICATE_COMPLETED" -> Decision.duplicateCompleted();
                case "PARAMETER_MISMATCH" -> Decision.parameterMismatch();
                default -> Decision.duplicatePending();
            };
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
            redisTemplate.execute(
                    MARK_COMPLETED_SCRIPT,
                    List.of(redisKey(scopedKey)),
                    PENDING_PREFIX + fingerprint,
                    COMPLETED_PREFIX + fingerprint,
                    ttlMillis(ttl)
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to mark HTTP idempotency key completed: key={}, reason={}", scopedKey, ex.getMessage());
        }
    }

    public void release(String scopedKey, String fingerprint) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_PENDING_SCRIPT,
                    List.of(redisKey(scopedKey)),
                    PENDING_PREFIX + fingerprint
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to release HTTP idempotency key: key={}, reason={}", scopedKey, ex.getMessage());
        }
    }

    private String redisKey(String scopedKey) {
        return KEY_PREFIX + scopedKey;
    }

    private String ttlMillis(Duration ttl) {
        return String.valueOf(Math.max(1L, ttl.toMillis()));
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
