package com.leo.erp.common.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpIdempotencyServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    {
        when(redisTemplate.opsForValue()).thenReturn(ops);
    }

    @Test
    void startAcquiresMissingKey() {
        when(ops.setIfAbsent(eq("http-idempotency:scope-1"), eq("PENDING:fingerprint-1"), any(Duration.class)))
                .thenReturn(true);
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.ACQUIRED);
    }

    @Test
    void startReturnsDuplicatePendingForSamePendingFingerprint() {
        when(ops.setIfAbsent(eq("http-idempotency:scope-1"), eq("PENDING:fingerprint-1"), any(Duration.class)))
                .thenReturn(false);
        when(ops.get("http-idempotency:scope-1")).thenReturn("PENDING:fingerprint-1");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.DUPLICATE_PENDING);
    }

    @Test
    void startReturnsDuplicateCompletedForSameCompletedFingerprint() {
        when(ops.setIfAbsent(eq("http-idempotency:scope-1"), eq("PENDING:fingerprint-1"), any(Duration.class)))
                .thenReturn(false);
        when(ops.get("http-idempotency:scope-1")).thenReturn("COMPLETED:fingerprint-1");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.DUPLICATE_COMPLETED);
    }

    @Test
    void startReturnsMismatchForDifferentFingerprint() {
        when(ops.setIfAbsent(eq("http-idempotency:scope-1"), eq("PENDING:fingerprint-1"), any(Duration.class)))
                .thenReturn(false);
        when(ops.get("http-idempotency:scope-1")).thenReturn("PENDING:fingerprint-2");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.PARAMETER_MISMATCH);
    }

    @Test
    void startFallsThroughWhenRedisUnavailable() {
        when(ops.setIfAbsent(eq("http-idempotency:scope-1"), eq("PENDING:fingerprint-1"), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.ACQUIRED);
    }

    @Test
    void markCompletedStoresCompletedFingerprint() {
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        service.markCompleted("scope-1", "fingerprint-1", Duration.ofHours(1));

        verify(ops).set("http-idempotency:scope-1", "COMPLETED:fingerprint-1", Duration.ofHours(1));
    }

    @Test
    void releaseDeletesKey() {
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        service.release("scope-1");

        verify(redisTemplate).delete("http-idempotency:scope-1");
    }
}
