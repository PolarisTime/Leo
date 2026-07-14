package com.leo.erp.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenBlacklistServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AccessTokenBlacklistService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valueOps = mock(ValueOperations.class);
        lenient().when(valueOps.get(anyString())).thenReturn("1700000000000");

        redisTemplate = mock(StringRedisTemplate.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(true);

        JwtProperties jwtProperties = new JwtProperties("leo", "secret", 3600000L, 86400000L);
        service = new AccessTokenBlacklistService(redisTemplate, jwtProperties);
    }

    @Test
    void shouldBlacklistUser() {
        service.blacklistUser(1L);
    }

    @Test
    void shouldReturnTrue_whenUserIsBlacklisted() {
        assertThat(service.isBlacklisted(1L)).isTrue();
    }

    @Test
    void shouldReturnFalse_whenUserIsNotBlacklisted() {
        StringRedisTemplate noKeyRedis = mock(StringRedisTemplate.class);
        when(noKeyRedis.hasKey(anyString())).thenReturn(false);

        AccessTokenBlacklistService svc = new AccessTokenBlacklistService(noKeyRedis, new JwtProperties("leo", "secret", 3600000L, 86400000L));

        assertThat(svc.isBlacklisted(1L)).isFalse();
    }

    @Test
    void shouldReturnBlacklistTime() {
        long blacklistTime = service.getBlacklistTime(1L);

        assertThat(blacklistTime).isEqualTo(1700000000000L);
    }

    @Test
    void shouldReturnZero_whenBlacklistTimeNotFound() {
        ValueOperations<String, String> nullOps = mock(ValueOperations.class);
        when(nullOps.get(anyString())).thenReturn(null);
        StringRedisTemplate nullRedis = mock(StringRedisTemplate.class);
        when(nullRedis.opsForValue()).thenReturn(nullOps);
        AccessTokenBlacklistService svc = new AccessTokenBlacklistService(nullRedis, new JwtProperties("leo", "secret", 3600000L, 86400000L));

        assertThat(svc.getBlacklistTime(1L)).isZero();
    }

    @Test
    void shouldBlacklistSession() {
        service.blacklistSession("session-001");
    }

    @Test
    void shouldReturnTrue_whenSessionIsBlacklisted() {
        assertThat(service.isSessionBlacklisted("session-001")).isTrue();
    }

    @Test
    void shouldReturnFalse_whenSessionIsNotBlacklisted() {
        StringRedisTemplate noKeyRedis = mock(StringRedisTemplate.class);
        when(noKeyRedis.hasKey(anyString())).thenReturn(false);
        AccessTokenBlacklistService svc = new AccessTokenBlacklistService(noKeyRedis, new JwtProperties("leo", "secret", 3600000L, 86400000L));

        assertThat(svc.isSessionBlacklisted("session-001")).isFalse();
    }
}
