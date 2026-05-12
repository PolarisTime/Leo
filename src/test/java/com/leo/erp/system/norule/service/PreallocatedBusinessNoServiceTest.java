package com.leo.erp.system.norule.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreallocatedBusinessNoServiceTest {

    @Test
    void shouldReserveAndConsumeForSameUser() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        AtomicReference<String> storedValue = new AtomicReference<>();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("leo:business-no:preallocated:purchase-order:123"), eq("1"), any(Duration.class)))
                .thenAnswer(invocation -> {
                    storedValue.set(invocation.getArgument(1));
                    return Boolean.TRUE;
                });
        when(valueOperations.get("leo:business-no:preallocated:purchase-order:123"))
                .thenAnswer(invocation -> storedValue.get());

        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "tester", List.of());

        service.reserve("purchase-order", 123L, principal);
        service.consumeOrThrow("purchase-order", 123L, principal);

        verify(redisTemplate).delete("leo:business-no:preallocated:purchase-order:123");
    }

    @Test
    void shouldRejectConsumptionByAnotherUser() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("leo:business-no:preallocated:purchase-order:123"))
                .thenReturn("1");

        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        assertThatThrownBy(() -> service.consumeOrThrow(
                "purchase-order",
                123L,
                SecurityPrincipal.authenticated(2L, "attacker", List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("预分配雪花ID无效或不属于当前用户");
    }
}
