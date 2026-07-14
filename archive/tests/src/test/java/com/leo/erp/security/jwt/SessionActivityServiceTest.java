package com.leo.erp.security.jwt;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
        lenient().when(redisTemplate.execute(any(), anyList(), any(), any(), any())).thenReturn(1L);
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
        AtomicReference<List<Object>> setArgs = new AtomicReference<>();
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("Script error"))
                .when(failingRedis).execute(any(), anyList(), any(), any(), any());
        lenient().when(failingRedis.opsForValue()).thenReturn(valueOperationsCapturingSet(setArgs));
        lenient().when(failingRedis.delete(anyString())).thenReturn(true);
        SessionActivityService fallbackService = new SessionActivityService(failingRedis, redisTuningProperties);

        fallbackService.touchSession("session-001");

        assertThat(setArgs.get())
                .hasSize(3)
                .first()
                .isEqualTo("session:activity:session-001");
        assertThat(setArgs.get().get(2)).isEqualTo(redisTuningProperties.sessionOnlineTtl());
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
    void shouldReturnEmptyMap_whenValuesIsEmpty() {
        ValueOperations<String, String> emptyOps = valueOperationsReturning(List.of());
        StringRedisTemplate emptyRedis = mock(StringRedisTemplate.class);
        when(emptyRedis.opsForValue()).thenReturn(emptyOps);
        SessionActivityService svc = new SessionActivityService(emptyRedis, redisTuningProperties);

        Map<String, java.time.LocalDateTime> result = svc.resolveLastActiveAt(List.of("sess-1"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreNullBlankAndDuplicateSessionIds() {
        AtomicReference<List<String>> keysRef = new AtomicReference<>();
        ValueOperations<String, String> ops = valueOperationsCapturingKeys(keysRef, List.of(String.valueOf(System.currentTimeMillis())));
        StringRedisTemplate capturingRedis = mock(StringRedisTemplate.class);
        when(capturingRedis.opsForValue()).thenReturn(ops);
        SessionActivityService svc = new SessionActivityService(capturingRedis, redisTuningProperties);

        Map<String, java.time.LocalDateTime> result = svc.resolveLastActiveAt(Arrays.asList(null, " ", "sess-1", "sess-1"));

        assertThat(result).containsOnlyKeys("sess-1");
        assertThat(keysRef.get()).containsExactly("session:activity:sess-1");
    }

    @Test
    void shouldIgnoreNullMultiGetValues() {
        ValueOperations<String, String> ops = valueOperationsReturning(Arrays.asList(null, String.valueOf(System.currentTimeMillis())));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        SessionActivityService svc = new SessionActivityService(redis, redisTuningProperties);

        Map<String, java.time.LocalDateTime> result = svc.resolveLastActiveAt(List.of("sess-1", "sess-2"));

        assertThat(result).containsOnlyKeys("sess-2");
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

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperationsReturning(List<String> values) {
        return valueOperationsCapturingKeys(new AtomicReference<>(), values);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperationsCapturingKeys(AtomicReference<List<String>> keysRef,
                                                                         List<String> values) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "multiGet" -> {
                        keysRef.set((List<String>) args[0]);
                        yield values;
                    }
                    case "toString" -> "ValueOperationsReturningStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperationsCapturingSet(AtomicReference<List<Object>> setArgsRef) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> {
                        setArgsRef.set(Arrays.asList(args));
                        yield null;
                    }
                    case "toString" -> "SetCapturingValueOperationsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
