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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PreallocatedBusinessNoServiceTest {

    @Test
    void shouldRejectReservationWhenRedisUnavailable() {
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(null);

        assertThatThrownBy(() -> service.reserve(
                "purchase-order",
                123L,
                SecurityPrincipal.authenticated(1L, "tester", List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("预分配雪花ID服务不可用");
    }

    @Test
    void shouldRejectBusinessNoReservationWhenKeyExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("leo:business-no:value:preallocated:purchase-order:PO-001"),
                eq("1"),
                any(Duration.class)
        )).thenReturn(Boolean.FALSE);

        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        assertThatThrownBy(() -> service.reserveBusinessNo(
                "purchase-order",
                "PO-001",
                SecurityPrincipal.authenticated(1L, "tester", List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("预分配业务单号失败，请重试");
    }

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
    void shouldAssertReservationWithoutConsuming() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("leo:business-no:preallocated:purchase-order:123"))
                .thenReturn("1");

        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        service.assertReservedByPrincipal(
                "purchase-order",
                123L,
                SecurityPrincipal.authenticated(1L, "tester", List.of())
        );

        verify(redisTemplate, never()).delete("leo:business-no:preallocated:purchase-order:123");
    }

    @Test
    void shouldRejectAssertionWhenRedisUnavailable() {
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(null);

        assertThatThrownBy(() -> service.assertReservedByPrincipal(
                "purchase-order",
                123L,
                SecurityPrincipal.authenticated(1L, "tester", List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("预分配雪花ID服务不可用");
    }

    @Test
    void shouldReserveAndConsumeBusinessNoForSameUser() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("leo:business-no:value:preallocated:purchase-order:PO-001"),
                eq("1"),
                any(Duration.class)
        )).thenReturn(Boolean.TRUE);
        when(valueOperations.get("leo:business-no:value:preallocated:purchase-order:PO-001"))
                .thenReturn("1");

        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "tester", List.of());

        service.reserveBusinessNo("purchase-order", "PO-001", principal);

        org.assertj.core.api.Assertions.assertThat(
                service.isBusinessNoReservedByPrincipal("purchase-order", "PO-001", principal)
        ).isTrue();

        service.consumeBusinessNo("purchase-order", "PO-001");
        verify(redisTemplate).delete("leo:business-no:value:preallocated:purchase-order:PO-001");
    }

    @Test
    void shouldReturnFalseWhenBusinessNoReservationCannotBeChecked() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreallocatedBusinessNoService serviceWithoutRedis = new PreallocatedBusinessNoService(null);
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "tester", List.of());

        org.assertj.core.api.Assertions.assertThat(
                serviceWithoutRedis.isBusinessNoReservedByPrincipal("purchase-order", "PO-001", principal)
        ).isFalse();
        org.assertj.core.api.Assertions.assertThat(
                service.isBusinessNoReservedByPrincipal("purchase-order", null, principal)
        ).isFalse();
        org.assertj.core.api.Assertions.assertThat(
                service.isBusinessNoReservedByPrincipal("purchase-order", " ", principal)
        ).isFalse();

        verify(redisTemplate, never()).opsForValue();
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

    @Test
    void shouldRejectConsumeWhenRedisUnavailable() {
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(null);

        assertThatThrownBy(() -> service.consume("purchase-order", 123L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预分配雪花ID服务不可用");
    }

    @Test
    void shouldConsumeReservedSnowflakeIdWithoutPrincipalCheck() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        service.consume(" purchase-order ", 123L);

        verify(redisTemplate).delete("leo:business-no:preallocated:purchase-order:123");
    }

    @Test
    void shouldIgnoreBlankBusinessNoConsumption() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreallocatedBusinessNoService serviceWithoutRedis = new PreallocatedBusinessNoService(null);
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        serviceWithoutRedis.consumeBusinessNo("purchase-order", "PO-001");
        service.consumeBusinessNo("purchase-order", null);
        service.consumeBusinessNo("purchase-order", " ");

        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectMissingPrincipal() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PreallocatedBusinessNoService service = new PreallocatedBusinessNoService(redisTemplate);

        assertThatThrownBy(() -> service.reserve("purchase-order", 123L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
        assertThatThrownBy(() -> service.reserve(
                "purchase-order",
                123L,
                new SecurityPrincipal(null, "tester", "", true, List.of())
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }
}
