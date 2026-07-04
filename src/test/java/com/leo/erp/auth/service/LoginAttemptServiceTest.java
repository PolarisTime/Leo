package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptServiceTest {

    @Test
    void shouldLockLoginAfterMaxFailures() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:tester")).thenReturn(1L, 2L, 3L);
        when(redisTemplate.hasKey("auth:login:lock:tester")).thenReturn(true);
        when(redisTemplate.getExpire("auth:login:lock:tester", TimeUnit.SECONDS)).thenReturn(120L);

        AuthProperties properties = new AuthProperties();
        properties.getLoginProtection().setMaxFailures(3);
        properties.getLoginProtection().setFailureWindowSeconds(300);
        properties.getLoginProtection().setLockDurationSeconds(120);

        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        service.recordFailure("tester");
        service.recordFailure("tester");
        service.recordFailure("tester");

        verify(redisTemplate).expire("auth:login:fail:tester", Duration.ofSeconds(300));
        verify(valueOperations).set(eq("auth:login:lock:tester"), any(String.class), eq(Duration.ofSeconds(120)));
        verify(redisTemplate).delete("auth:login:fail:tester");
        assertThatThrownBy(() -> service.ensureLoginAllowed("tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    void shouldAllowLoginWhenNotLocked() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:login:lock:tester")).thenReturn(false);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        org.assertj.core.api.Assertions.assertThatCode(() -> service.ensureLoginAllowed("tester"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowLoginWhenProtectionDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.getLoginProtection().setEnabled(false);

        LoginAttemptService service = new LoginAttemptService(mock(StringRedisTemplate.class), properties);

        org.assertj.core.api.Assertions.assertThatCode(() -> service.ensureLoginAllowed("tester"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipRecordFailureWhenProtectionDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.getLoginProtection().setEnabled(false);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        service.recordFailure("tester");

        verify(redisTemplate, org.mockito.Mockito.never()).opsForValue();
    }

    @Test
    void shouldClearFailuresAndLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.delete("auth:login:fail:tester")).thenReturn(true);
        when(redisTemplate.delete("auth:login:lock:tester")).thenReturn(true);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        service.clearFailures("tester");

        verify(redisTemplate).delete("auth:login:fail:tester");
        verify(redisTemplate).delete("auth:login:lock:tester");
    }

    @Test
    void shouldNormalizeNullLoginNameWhenClearingFailures() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        service.clearFailures(null);

        verify(redisTemplate).delete("auth:login:fail:");
        verify(redisTemplate).delete("auth:login:lock:");
    }

    @Test
    void shouldSkipClearFailuresWhenProtectionDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.getLoginProtection().setEnabled(false);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        service.clearFailures("tester");

        verify(redisTemplate, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldShowMinutesAndSecondsWhenLockDurationOver60() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:login:lock:tester")).thenReturn(true);
        when(redisTemplate.getExpire("auth:login:lock:tester", TimeUnit.SECONDS)).thenReturn(125L);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        assertThatThrownBy(() -> service.ensureLoginAllowed("tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 分钟 5 秒");
    }

    @Test
    void shouldShowOnlyMinutesWhenSecondsIsZero() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:login:lock:tester")).thenReturn(true);
        when(redisTemplate.getExpire("auth:login:lock:tester", TimeUnit.SECONDS)).thenReturn(120L);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        assertThatThrownBy(() -> service.ensureLoginAllowed("tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 分钟")
                .hasMessageNotContaining("0 秒");
    }

    @Test
    void shouldShowSecondsWhenUnder60() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:login:lock:tester")).thenReturn(true);
        when(redisTemplate.getExpire("auth:login:lock:tester", TimeUnit.SECONDS)).thenReturn(45L);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        assertThatThrownBy(() -> service.ensureLoginAllowed("tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("45 秒");
    }

    @Test
    void shouldHandleNullFailureCount() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:tester")).thenReturn(null);

        AuthProperties properties = new AuthProperties();
        LoginAttemptService service = new LoginAttemptService(redisTemplate, properties);

        org.assertj.core.api.Assertions.assertThatCode(() -> service.recordFailure("tester"))
                .doesNotThrowAnyException();
    }
}
