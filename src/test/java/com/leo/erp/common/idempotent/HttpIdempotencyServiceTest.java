package com.leo.erp.common.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpIdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @Test
    void startAcquiresMissingKey() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("http-idempotency:scope-1")), any(), any(), any(), any(), any()))
                .thenReturn("ACQUIRED");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.ACQUIRED);
    }

    @Test
    void startReturnsDuplicatePendingForSamePendingFingerprint() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("http-idempotency:scope-1")), any(), any(), any(), any(), any()))
                .thenReturn("DUPLICATE_PENDING");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.DUPLICATE_PENDING);
    }

    @Test
    void startReturnsDuplicateCompletedForSameCompletedFingerprint() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("http-idempotency:scope-1")), any(), any(), any(), any(), any()))
                .thenReturn("DUPLICATE_COMPLETED");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.DUPLICATE_COMPLETED);
    }

    @Test
    void startReturnsMismatchForDifferentFingerprint() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("http-idempotency:scope-1")), any(), any(), any(), any(), any()))
                .thenReturn("PARAMETER_MISMATCH");
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        HttpIdempotencyService.Decision decision =
                service.start("scope-1", "fingerprint-1", Duration.ofHours(1));

        assertThat(decision.status()).isEqualTo(HttpIdempotencyService.Status.PARAMETER_MISMATCH);
    }

    @Test
    void startFallsThroughWhenRedisUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("http-idempotency:scope-1")), any(), any(), any(), any(), any()))
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

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("http-idempotency:scope-1")),
                eq("PENDING:fingerprint-1"),
                eq("COMPLETED:fingerprint-1"),
                eq("3600000")
        );
    }

    @Test
    void releaseDeletesOnlyMatchingPendingKey() {
        HttpIdempotencyService service = new HttpIdempotencyService(redisTemplate);

        service.release("scope-1", "fingerprint-1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("http-idempotency:scope-1")),
                eq("PENDING:fingerprint-1")
        );
    }
}
