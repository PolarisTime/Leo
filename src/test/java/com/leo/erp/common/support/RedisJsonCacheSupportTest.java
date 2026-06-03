package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisJsonCacheSupportTest {

    private ObjectMapper objectMapper;
    private RedisTuningProperties redisTuningProperties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisJsonCacheSupport support;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        redisTuningProperties = new RedisTuningProperties();

        valueOps = mock(ValueOperations.class);
        lenient().when(valueOps.get(anyString())).thenReturn("\"cached-value\"");

        redisTemplate = mock(StringRedisTemplate.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        lenient().when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation ->
                (long) ((Collection<?>) invocation.getArgument(0)).size());

        support = new RedisJsonCacheSupport(redisTemplate, objectMapper, redisTuningProperties);
    }

    @Test
    void shouldReturnCachedValue_whenGetOrLoadWithTypeReference() {
        String result = support.getOrLoad("test-key", Duration.ofMinutes(5),
                new TypeReference<String>() {}, () -> "loaded");

        assertThat(result).isEqualTo("cached-value");
    }

    @Test
    void shouldLoadAndCache_whenCacheMissWithTypeReference() {
        ValueOperations<String, String> missOps = mock(ValueOperations.class);
        when(missOps.get(anyString())).thenReturn(null);
        StringRedisTemplate missRedis = mock(StringRedisTemplate.class);
        when(missRedis.opsForValue()).thenReturn(missOps);
        RedisJsonCacheSupport missSupport = new RedisJsonCacheSupport(missRedis, objectMapper, redisTuningProperties);

        String result = missSupport.getOrLoad("test-key", Duration.ofMinutes(5),
                new TypeReference<String>() {}, () -> "loaded-value");

        assertThat(result).isEqualTo("loaded-value");
    }

    @Test
    void shouldReturnCachedValue_whenGetOrLoadWithClass() {
        String result = support.getOrLoad("test-key", Duration.ofMinutes(5), String.class, () -> "loaded");

        assertThat(result).isEqualTo("cached-value");
    }

    @Test
    void shouldLoadAndCache_whenCacheMissWithClass() {
        ValueOperations<String, String> missOps = mock(ValueOperations.class);
        when(missOps.get(anyString())).thenReturn(null);
        StringRedisTemplate missRedis = mock(StringRedisTemplate.class);
        when(missRedis.opsForValue()).thenReturn(missOps);
        RedisJsonCacheSupport missSupport = new RedisJsonCacheSupport(missRedis, objectMapper, redisTuningProperties);

        String result = missSupport.getOrLoad("test-key", Duration.ofMinutes(5), String.class, () -> "loaded-value");

        assertThat(result).isEqualTo("loaded-value");
    }

    @Test
    void shouldDeleteAndReload_whenCacheHasInvalidJson() {
        ValueOperations<String, String> badOps = mock(ValueOperations.class);
        when(badOps.get(anyString())).thenReturn("{invalid-json}");
        StringRedisTemplate badRedis = mock(StringRedisTemplate.class);
        when(badRedis.opsForValue()).thenReturn(badOps);
        when(badRedis.delete(anyString())).thenReturn(true);
        RedisJsonCacheSupport badSupport = new RedisJsonCacheSupport(badRedis, objectMapper, redisTuningProperties);

        String result = badSupport.getOrLoad("test-key", Duration.ofMinutes(5),
                new TypeReference<String>() {}, () -> "reloaded");

        assertThat(result).isEqualTo("reloaded");
    }

    @Test
    void shouldFallbackToLoader_whenRedisThrows() {
        ValueOperations<String, String> errOps = mock(ValueOperations.class);
        when(errOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        StringRedisTemplate errRedis = mock(StringRedisTemplate.class);
        when(errRedis.opsForValue()).thenReturn(errOps);
        RedisJsonCacheSupport errSupport = new RedisJsonCacheSupport(errRedis, objectMapper, redisTuningProperties);

        String result = errSupport.getOrLoad("test-key", Duration.ofMinutes(5),
                new TypeReference<String>() {}, () -> "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void shouldWriteToRedis() {
        support.write("test-key", "test-value", Duration.ofMinutes(5));
    }

    @Test
    void shouldSkipWrite_whenRedisTemplateIsNull() {
        RedisJsonCacheSupport nullSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        nullSupport.write("test-key", "test-value", Duration.ofMinutes(5));
    }

    @Test
    void shouldSkipWrite_whenValueIsNull() {
        support.write("test-key", null, Duration.ofMinutes(5));
    }

    @Test
    void shouldSkipWrite_whenTtlIsNull() {
        support.write("test-key", "test-value", null);
    }

    @Test
    void shouldHandleSerializationFailureGracefully() {
        ObjectMapper failingMapper = new ObjectMapper();
        RedisJsonCacheSupport failingSupport = new RedisJsonCacheSupport(redisTemplate, failingMapper, redisTuningProperties);
        Object cyclic = new Object() {
            @Override
            public String toString() {
                return "cyclic";
            }
        };
        failingSupport.write("test-key", cyclic, Duration.ofMinutes(5));
    }

    @Test
    void shouldDeleteSingleKey() {
        support.delete("test-key");
    }

    @Test
    void shouldSkipDelete_whenKeyIsNull() {
        support.delete((String) null);
    }

    @Test
    void shouldDeleteCollection() {
        support.delete(List.of("key1", "key2"));
    }

    @Test
    void shouldSkipDelete_whenCollectionIsNull() {
        support.delete((Collection<String>) null);
    }

    @Test
    void shouldSkipDelete_whenCollectionIsEmpty() {
        support.delete(List.of());
    }

    @Test
    void shouldDeleteByPattern() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any())).thenReturn(new ByteArrayCursor(List.of(
                "key:1".getBytes(StandardCharsets.UTF_8),
                "key:2".getBytes(StandardCharsets.UTF_8))));
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate scanRedis = mock(StringRedisTemplate.class);
        when(scanRedis.opsForValue()).thenReturn(valueOps);
        when(scanRedis.getConnectionFactory()).thenReturn(connectionFactory);
        when(scanRedis.delete(any(Collection.class))).thenReturn(2L);
        RedisJsonCacheSupport scanSupport = new RedisJsonCacheSupport(scanRedis, objectMapper, redisTuningProperties);

        scanSupport.deleteByPattern("key:*");
    }

    @Test
    void shouldSkipDeleteByPattern_whenPatternIsBlank() {
        support.deleteByPattern("");
    }

    @Test
    void shouldSkipDeleteByPattern_whenPatternIsNull() {
        support.deleteByPattern(null);
    }

    @Test
    void shouldSkipDeleteByPattern_whenRedisTemplateIsNull() {
        RedisJsonCacheSupport nullSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        nullSupport.deleteByPattern("key:*");
    }

    @Test
    void shouldSkipDeleteByPattern_whenConnectionFactoryIsNull() {
        StringRedisTemplate noFactoryRedis = mock(StringRedisTemplate.class);
        when(noFactoryRedis.getConnectionFactory()).thenReturn(null);
        RedisJsonCacheSupport noFactorySupport = new RedisJsonCacheSupport(noFactoryRedis, objectMapper, redisTuningProperties);
        noFactorySupport.deleteByPattern("key:*");
    }

    @Test
    void shouldHandleRedisExceptionInDeleteByPattern() {
        RedisConnectionFactory errFactory = mock(RedisConnectionFactory.class);
        when(errFactory.getConnection()).thenThrow(new RuntimeException("Connection error"));
        StringRedisTemplate errRedis = mock(StringRedisTemplate.class);
        when(errRedis.getConnectionFactory()).thenReturn(errFactory);
        RedisJsonCacheSupport errSupport = new RedisJsonCacheSupport(errRedis, objectMapper, redisTuningProperties);
        errSupport.deleteByPattern("key:*");
    }

    @Test
    void shouldHandleConnectionCloseException() {
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any())).thenReturn(new ByteArrayCursor(List.of()));
        doAnswer(invocation -> {
            closeCalled.set(true);
            throw new RuntimeException("Close error");
        }).when(connection).close();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate closeRedis = mock(StringRedisTemplate.class);
        when(closeRedis.getConnectionFactory()).thenReturn(connectionFactory);
        when(closeRedis.delete(any(Collection.class))).thenReturn(1L);
        RedisJsonCacheSupport closeSupport = new RedisJsonCacheSupport(closeRedis, objectMapper, redisTuningProperties);
        closeSupport.deleteByPattern("key:*");
        assertThat(closeCalled.get()).isTrue();
    }

    @Test
    void shouldRespectMaxScanLimit() {
        RedisTuningProperties limitedProps = new RedisTuningProperties();
        limitedProps.getScan().setMaxKeys(1);
        limitedProps.getScan().setDeleteBatchSize(1);

        List<byte[]> manyKeys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            manyKeys.add(("key:" + i).getBytes(StandardCharsets.UTF_8));
        }
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any())).thenReturn(new ByteArrayCursor(manyKeys));
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate limitRedis = mock(StringRedisTemplate.class);
        when(limitRedis.getConnectionFactory()).thenReturn(connectionFactory);
        when(limitRedis.delete(any(Collection.class))).thenReturn(1L);
        RedisJsonCacheSupport limitSupport = new RedisJsonCacheSupport(limitRedis, objectMapper, limitedProps);
        limitSupport.deleteByPattern("key:*");
    }

    private static final class ByteArrayCursor implements Cursor<byte[]> {
        private final List<byte[]> values;
        private final Iterator<byte[]> iterator;
        private boolean closed;
        private long position;

        private ByteArrayCursor(List<byte[]> values) {
            this.values = values;
            this.iterator = values.iterator();
        }

        @Override
        public CursorId getId() { return CursorId.of(1L); }

        @Override
        public long getCursorId() { return 1L; }

        @Override
        public boolean isClosed() { return closed; }

        @Override
        public long getPosition() { return position; }

        @Override
        public boolean hasNext() { return iterator.hasNext(); }

        @Override
        public byte[] next() { position++; return iterator.next(); }

        @Override
        public void close() { closed = true; }

        @Override
        public void remove() { throw new UnsupportedOperationException("remove"); }
    }
}
