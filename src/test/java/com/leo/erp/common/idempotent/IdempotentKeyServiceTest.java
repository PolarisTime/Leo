package com.leo.erp.common.idempotent;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentKeyServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    {
        when(redis.opsForValue()).thenReturn(ops);
    }

    // ── tryAcquire ──

    @Test
    void tryAcquireSuccessWhenKeyNotExists() {
        when(ops.setIfAbsent(eq("idempotent:pay-001"), eq("__PENDING__"), any(Duration.class)))
                .thenReturn(true);
        IdempotentKeyService service = new IdempotentKeyService(redis);

        boolean acquired = service.tryAcquire("pay-001", Duration.ofHours(1));

        assertThat(acquired).isTrue();
    }

    @Test
    void tryAcquireFailsWhenKeyExists() {
        when(ops.setIfAbsent(eq("idempotent:pay-001"), eq("__PENDING__"), any(Duration.class)))
                .thenReturn(false);
        IdempotentKeyService service = new IdempotentKeyService(redis);

        boolean acquired = service.tryAcquire("pay-001", Duration.ofHours(1));

        assertThat(acquired).isFalse();
    }

    @Test
    void tryAcquireReturnsTrueWhenRedisUnavailable() {
        IdempotentKeyService service = new IdempotentKeyService(null);

        boolean acquired = service.tryAcquire("pay-001", Duration.ofHours(1));

        assertThat(acquired).isTrue();
    }

    // ── markCompleted ──

    @Test
    void markCompletedSetsResultInRedis() {
        IdempotentKeyService service = new IdempotentKeyService(redis);

        service.markCompleted("pay-001", "completed", Duration.ofHours(1));

        verify(ops).set("idempotent:pay-001", "completed", Duration.ofHours(1));
    }

    @Test
    void markCompletedNoopsWhenRedisUnavailable() {
        IdempotentKeyService service = new IdempotentKeyService(null);

        service.markCompleted("pay-001", "completed", Duration.ofHours(1));
    }

    // ── getResult ──

    @Test
    void getResultReturnsValueWhenCompleted() {
        when(ops.get("idempotent:pay-001")).thenReturn("completed");
        IdempotentKeyService service = new IdempotentKeyService(redis);

        Optional<String> result = service.getResult("pay-001");

        assertThat(result).hasValue("completed");
    }

    @Test
    void getResultReturnsEmptyWhenPending() {
        when(ops.get("idempotent:pay-001")).thenReturn("__PENDING__");
        IdempotentKeyService service = new IdempotentKeyService(redis);

        Optional<String> result = service.getResult("pay-001");

        assertThat(result).isEmpty();
    }

    @Test
    void getResultReturnsEmptyWhenNull() {
        when(ops.get("idempotent:pay-001")).thenReturn(null);
        IdempotentKeyService service = new IdempotentKeyService(redis);

        Optional<String> result = service.getResult("pay-001");

        assertThat(result).isEmpty();
    }

    @Test
    void getResultReturnsEmptyWhenRedisUnavailable() {
        IdempotentKeyService service = new IdempotentKeyService(null);

        Optional<String> result = service.getResult("pay-001");

        assertThat(result).isEmpty();
    }

    // ── release ──

    @Test
    void releaseDeletesKey() {
        IdempotentKeyService service = new IdempotentKeyService(redis);

        service.release("pay-001");

        verify(redis).delete("idempotent:pay-001");
    }

    @Test
    void releaseNoopsWhenRedisUnavailable() {
        IdempotentKeyService service = new IdempotentKeyService(null);

        service.release("pay-001");
    }

    // ── throwIfDuplicate ──

    @Test
    void throwIfDuplicateThrowsBusinessException() {
        IdempotentKeyService service = new IdempotentKeyService(redis);

        assertThatThrownBy(() -> service.throwIfDuplicate("pay-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请勿重复提交")
                .hasMessageContaining("pay-001");
    }
}
