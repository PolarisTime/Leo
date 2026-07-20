package com.leo.erp.common.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
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
            local completed_with_response = ARGV[2] .. ':'
            if string.sub(current, 1, string.len(completed_with_response)) == completed_with_response then
                return 'DUPLICATE_COMPLETED:' .. string.sub(current, string.len(completed_with_response) + 1)
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
    private static final String DUPLICATE_COMPLETED_WITH_RESPONSE_PREFIX = "DUPLICATE_COMPLETED:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public HttpIdempotencyService(@Nullable StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Decision start(String scopedKey, String fingerprint, Duration ttl) {
        if (redisTemplate == null) {
            log.error("HTTP idempotency Redis template is unavailable: key={}", scopedKey);
            return Decision.unavailable();
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
            String resolvedStatus = status == null ? "" : status;
            if (resolvedStatus.startsWith(DUPLICATE_COMPLETED_WITH_RESPONSE_PREFIX)) {
                return decodeCompletedResponse(
                        scopedKey,
                        resolvedStatus.substring(DUPLICATE_COMPLETED_WITH_RESPONSE_PREFIX.length())
                );
            }
            return switch (resolvedStatus) {
                case "ACQUIRED" -> Decision.acquired();
                case "DUPLICATE_PENDING" -> Decision.duplicatePending();
                case "DUPLICATE_COMPLETED" -> Decision.duplicateCompleted();
                case "PARAMETER_MISMATCH" -> Decision.parameterMismatch();
                default -> Decision.unavailable();
            };
        } catch (RuntimeException ex) {
            log.error(
                    "HTTP idempotency Redis unavailable, request is rejected: key={}, reason={}",
                    scopedKey,
                    ex.getMessage()
            );
            return Decision.unavailable();
        }
    }

    public boolean markCompleted(String scopedKey,
                                 String fingerprint,
                                 Duration ttl,
                                 CachedResponse response) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String encodedResponse = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(response));
            Long updated = redisTemplate.execute(
                    MARK_COMPLETED_SCRIPT,
                    List.of(redisKey(scopedKey)),
                    PENDING_PREFIX + fingerprint,
                    COMPLETED_PREFIX + fingerprint + ":" + encodedResponse,
                    ttlMillis(ttl)
            );
            return updated != null && updated == 1L;
        } catch (Exception ex) {
            log.warn("Failed to mark HTTP idempotency key completed: key={}, reason={}", scopedKey, ex.getMessage());
            return false;
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

    private Decision decodeCompletedResponse(String scopedKey, String encodedResponse) {
        try {
            byte[] json = Base64.getDecoder().decode(encodedResponse);
            return Decision.duplicateCompleted(objectMapper.readValue(json, CachedResponse.class));
        } catch (Exception ex) {
            log.error("Failed to decode cached HTTP idempotency response: key={}, reason={}", scopedKey, ex.getMessage());
            return Decision.unavailable();
        }
    }

    public record CachedResponse(
            int status,
            String contentType,
            String bodyBase64
    ) {
        public CachedResponse {
            bodyBase64 = bodyBase64 == null ? "" : bodyBase64;
        }

        public byte[] body() {
            return Base64.getDecoder().decode(bodyBase64);
        }
    }

    public record Decision(Status status, CachedResponse response) {

        static Decision acquired() {
            return new Decision(Status.ACQUIRED, null);
        }

        static Decision duplicatePending() {
            return new Decision(Status.DUPLICATE_PENDING, null);
        }

        static Decision duplicateCompleted() {
            return duplicateCompleted(null);
        }

        static Decision duplicateCompleted(CachedResponse response) {
            return new Decision(Status.DUPLICATE_COMPLETED, response);
        }

        static Decision parameterMismatch() {
            return new Decision(Status.PARAMETER_MISMATCH, null);
        }

        static Decision unavailable() {
            return new Decision(Status.UNAVAILABLE, null);
        }
    }

    public enum Status {
        ACQUIRED,
        DUPLICATE_PENDING,
        DUPLICATE_COMPLETED,
        PARAMETER_MISMATCH,
        UNAVAILABLE
    }
}
