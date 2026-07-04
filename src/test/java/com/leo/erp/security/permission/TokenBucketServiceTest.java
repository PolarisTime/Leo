package com.leo.erp.security.permission;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TokenBucketServiceTest {

    @Test
    void tryConsumeShouldReturnAllowFallbackWhenRedisReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(null).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString());

        TokenBucketService service = new TokenBucketService(redisTemplate, new RedisTuningProperties());

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void tryConsumeShouldReturnAllowFallbackWhenRedisReturnsShortResult() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(List.of(1L, 50L)).when(redisTemplate)
                .execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString());

        TokenBucketService service = new TokenBucketService(redisTemplate, new RedisTuningProperties());

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void tryConsumeShouldReturnAllowedWhenTokensAvailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(List.of(1L, 50L, 0L)).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString());

        TokenBucketService service = new TokenBucketService(redisTemplate, new RedisTuningProperties());

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(50L);
        assertThat(result.retryAfterMs()).isEqualTo(0L);
    }

    @Test
    void tryConsumeShouldReturnDeniedWhenBucketEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(List.of(0L, 0L, 1000L)).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString());

        TokenBucketService service = new TokenBucketService(redisTemplate, new RedisTuningProperties());

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0L);
        assertThat(result.retryAfterMs()).isEqualTo(1000L);
    }

    @Test
    void tryConsumeShouldReturnAllowFallbackOnException() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("Redis unavailable")).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString());

        TokenBucketService service = new TokenBucketService(redisTemplate, new RedisTuningProperties());

        TokenBucketService.TokenBucketResult result = service.tryConsume("test-key", 1);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void retryAfterSecondsShouldRoundUp() {
        TokenBucketService.TokenBucketResult result = new TokenBucketService.TokenBucketResult(false, 0, 1500);

        assertThat(result.retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void retryAfterSecondsShouldReturnAtLeast1() {
        TokenBucketService.TokenBucketResult result = new TokenBucketService.TokenBucketResult(false, 0, 0);

        assertThat(result.retryAfterSeconds()).isEqualTo(1);
    }
}
