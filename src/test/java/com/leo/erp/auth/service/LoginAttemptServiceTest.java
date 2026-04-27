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
}
