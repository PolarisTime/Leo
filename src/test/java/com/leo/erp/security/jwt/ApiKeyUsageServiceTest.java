package com.leo.erp.security.jwt;

import com.leo.erp.auth.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyUsageServiceTest {

    @Test
    void shouldPersistLastUsedAtOnlyWhenThrottleKeyCreated() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("api-key:usage:99"), any(String.class), eq(Duration.ofMinutes(1))))
                .thenReturn(Boolean.TRUE);

        ApiKeyUsageService service = new ApiKeyUsageService(apiKeyRepository, redisTemplate);

        service.markUsed(99L);

        verify(apiKeyRepository).updateLastUsedAt(eq(99L), any());
    }

    @Test
    void shouldSkipDatabaseWriteWhenThrottleKeyAlreadyExists() {
        ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("api-key:usage:99"), any(String.class), eq(Duration.ofMinutes(1))))
                .thenReturn(Boolean.FALSE);

        ApiKeyUsageService service = new ApiKeyUsageService(apiKeyRepository, redisTemplate);

        service.markUsed(99L);

        verify(apiKeyRepository, never()).updateLastUsedAt(any(), any());
    }
}
