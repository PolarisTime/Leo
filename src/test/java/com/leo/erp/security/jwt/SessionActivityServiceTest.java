package com.leo.erp.security.jwt;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionActivityServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisTuningProperties redisTuningProperties;
    private SessionActivityService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTuningProperties = new RedisTuningProperties();

        valueOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> null;
                    case "get" -> String.valueOf(System.currentTimeMillis());
                    case "multiGet" -> List.of(String.valueOf(System.currentTimeMillis()), String.valueOf(System.currentTimeMillis() + 1_000));
                    case "toString" -> "ValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        redisTemplate = mock(StringRedisTemplate.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);

        service = new SessionActivityService(redisTemplate, redisTuningProperties);
    }

    @Test
    void shouldSkipTouch_whenSessionIdIsNull() {
        service.touchSession(null);
    }

    @Test
    void shouldSkipTouch_whenSessionIdIsBlank() {
        service.touchSession("  ");
    }

    @Test
    void shouldTouchSessionSuccessfully() {
        service.touchSession("session-001");
    }

    @Test
    void shouldFallbackToDirectSet_whenRedisScriptFails() {
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        when(failingRedis.execute(any(), anyList(), any())).thenThrow(new RuntimeException("Script error"));
        lenient().when(failingRedis.opsForValue()).thenReturn(valueOps);
        lenient().when(failingRedis.delete(anyString())).thenReturn(true);
        SessionActivityService fallbackService = new SessionActivityService(failingRedis, redisTuningProperties);

        fallbackService.touchSession("session-001");
    }

    @Test
    void shouldSkipClear_whenSessionIdIsNull() {
        service.clearSession(null);
    }

    @Test
    void shouldSkipClear_whenSessionIdIsBlank() {
        service.clearSession("  ");
    }

    @Test
    void shouldClearSession() {
        service.clearSession("session-001");
    }

    @Test
    void shouldResolveLastActiveAt() {
        Map<String, java.time.LocalDateTime> result = service.resolveLastActiveAt(List.of("sess-1", "sess-2"));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnEmptyMap_whenSessionIdsIsNull() {
        Map<String, java.time.LocalDateTime> result = service.resolveLastActiveAt(null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMap_whenSessionIdsIsEmpty() {
        Map<String, java.time.LocalDateTime> result = service.resolveLastActiveAt(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMap_whenValuesIsNull() {
        ValueOperations<String, String> nullOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "multiGet" -> null;
                    case "toString" -> "NullValueOpsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        StringRedisTemplate nullRedis = mock(StringRedisTemplate.class);
        when(nullRedis.opsForValue()).thenReturn(nullOps);
        SessionActivityService svc = new SessionActivityService(nullRedis, redisTuningProperties);

        Map<String, java.time.LocalDateTime> result = svc.resolveLastActiveAt(List.of("sess-1"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreInvalidTimestamps() {
        ValueOperations<String, String> badOps = (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "multiGet" -> List.of("not-a-number", "");
                    case "toString" -> "BadValueOpsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        StringRedisTemplate badRedis = mock(StringRedisTemplate.class);
        when(badRedis.opsForValue()).thenReturn(badOps);
        SessionActivityService svc = new SessionActivityService(badRedis, redisTuningProperties);

        Map<String, java.time.LocalDateTime> result = svc.resolveLastActiveAt(List.of("sess-1", "sess-2"));

        assertThat(result).isEmpty();
    }
}
