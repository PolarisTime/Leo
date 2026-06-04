package com.leo.erp.common.idempotent;

import com.leo.erp.common.error.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    private IdempotentAspect aspect;
    private IdempotentKeyService keyService;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        keyService = mock(IdempotentKeyService.class);
        when(keyService.tryAcquire(any(), any())).thenReturn(true);
        aspect = new IdempotentAspect(keyService);
        joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test-key"});
        when(signature.toShortString()).thenReturn("TestService.test()");
        when(signature.getParameterNames()).thenReturn(new String[]{"key"});
    }

    @Test
    void shouldProceed_whenKeyIsBlank() throws Throwable {
        Idempotent idempotent = mockIdempotent("");
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.enforceIdempotency(joinPoint, idempotent);

        assertThat(result).isEqualTo("result");
        verify(keyService, never()).tryAcquire(any(), any());
        verify(keyService, never()).markCompleted(any(), any(), any());
    }

    @Test
    void shouldAcquireAndComplete_whenKeyAvailable() throws Throwable {
        Idempotent idempotent = mockIdempotent("#key");
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.enforceIdempotency(joinPoint, idempotent);

        assertThat(result).isEqualTo("success");
        verify(keyService).tryAcquire("TestService.test():test-key", Duration.ofHours(1));
        verify(keyService).markCompleted("TestService.test():test-key", "completed", Duration.ofHours(1));
        verify(keyService, never()).release(any());
    }

    @Test
    void shouldThrowDuplicate_whenKeyAlreadyAcquired() throws Throwable {
        IdempotentKeyService duplicateService = mock(IdempotentKeyService.class);
        when(duplicateService.tryAcquire(any(), any())).thenReturn(false);
        doThrow(new BusinessException(null, "请勿重复提交: test-key")).when(duplicateService).throwIfDuplicate(any());
        IdempotentAspect duplicateAspect = new IdempotentAspect(duplicateService);
        Idempotent idempotent = mockIdempotent("#key");

        assertThatThrownBy(() -> duplicateAspect.enforceIdempotency(joinPoint, idempotent))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请勿重复提交");
        verify(joinPoint, never()).proceed();
        verify(duplicateService, never()).markCompleted(any(), any(), any());
        verify(duplicateService, never()).release(any());
    }

    @Test
    void shouldReleaseKey_whenProceedThrows() throws Throwable {
        Idempotent idempotent = mockIdempotent("#key");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        assertThatThrownBy(() -> aspect.enforceIdempotency(joinPoint, idempotent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("业务异常");
        verify(keyService).release("TestService.test():test-key");
        verify(keyService, never()).markCompleted(any(), any(), any());
    }

    @Test
    void shouldHandleNullParameterNames() throws Throwable {
        MethodSignature nullSig = mock(MethodSignature.class);
        when(nullSig.toShortString()).thenReturn("TestService.test()");
        when(nullSig.getParameterNames()).thenReturn(null);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(nullSig);
        when(jp.getArgs()).thenReturn(new Object[]{"test-key"});
        when(jp.proceed()).thenReturn("result");
        Idempotent idempotent = mockIdempotent("#key");

        Object result = aspect.enforceIdempotency(jp, idempotent);

        assertThat(result).isEqualTo("result");
    }

    private Idempotent mockIdempotent(String key) {
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn(key);
        when(idempotent.ttlSeconds()).thenReturn(3600L);
        return idempotent;
    }
}
