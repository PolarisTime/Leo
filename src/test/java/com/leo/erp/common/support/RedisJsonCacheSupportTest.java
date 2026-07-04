package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldReturnEmpty_whenReadTypeReferenceArgumentsAreMissing() {
        RedisJsonCacheSupport nullRedisSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        RedisJsonCacheSupport nullMapperSupport = new RedisJsonCacheSupport(redisTemplate, null, redisTuningProperties);

        assertThat(nullRedisSupport.read("test-key", new TypeReference<String>() {})).isEmpty();
        assertThat(nullMapperSupport.read("test-key", new TypeReference<String>() {})).isEmpty();
        assertThat(support.read(null, new TypeReference<String>() {})).isEmpty();
        assertThat(support.read("test-key", (TypeReference<String>) null)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenReadClassArgumentsAreMissing() {
        RedisJsonCacheSupport nullRedisSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        RedisJsonCacheSupport nullMapperSupport = new RedisJsonCacheSupport(redisTemplate, null, redisTuningProperties);

        assertThat(nullRedisSupport.read("test-key", String.class)).isEmpty();
        assertThat(nullMapperSupport.read("test-key", String.class)).isEmpty();
        assertThat(support.read(null, String.class)).isEmpty();
        assertThat(support.read("test-key", (Class<String>) null)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenCachedValueIsBlank() {
        ValueOperations<String, String> blankOps = mock(ValueOperations.class);
        when(blankOps.get("blank-key")).thenReturn(" ");
        StringRedisTemplate blankRedis = mock(StringRedisTemplate.class);
        when(blankRedis.opsForValue()).thenReturn(blankOps);
        RedisJsonCacheSupport blankSupport = new RedisJsonCacheSupport(blankRedis, objectMapper, redisTuningProperties);

        assertThat(blankSupport.read("blank-key", String.class)).isEmpty();
        assertThat(blankSupport.read("blank-key", new TypeReference<String>() {})).isEmpty();
    }

    @Test
    void shouldDeleteAndReturnEmpty_whenClassReadHasInvalidJson() {
        ValueOperations<String, String> badOps = mock(ValueOperations.class);
        when(badOps.get("bad-key")).thenReturn("{invalid-json}");
        StringRedisTemplate badRedis = mock(StringRedisTemplate.class);
        when(badRedis.opsForValue()).thenReturn(badOps);
        when(badRedis.delete("bad-key")).thenReturn(true);
        RedisJsonCacheSupport badSupport = new RedisJsonCacheSupport(badRedis, objectMapper, redisTuningProperties);

        assertThat(badSupport.read("bad-key", String.class)).isEmpty();

        verify(badRedis).delete("bad-key");
    }

    @Test
    void shouldReturnEmpty_whenClassReadThrowsRedisException() {
        ValueOperations<String, String> errOps = mock(ValueOperations.class);
        when(errOps.get("test-key")).thenThrow(new RuntimeException("Redis down"));
        StringRedisTemplate errRedis = mock(StringRedisTemplate.class);
        when(errRedis.opsForValue()).thenReturn(errOps);
        RedisJsonCacheSupport errSupport = new RedisJsonCacheSupport(errRedis, objectMapper, redisTuningProperties);

        assertThat(errSupport.read("test-key", String.class)).isEmpty();
    }

    @Test
    void shouldWriteToRedis() {
        support.write("test-key", "test-value", Duration.ofMinutes(5));
    }

    @Test
    void shouldSkipWrite_whenObjectMapperIsNull() {
        RedisJsonCacheSupport nullSupport = new RedisJsonCacheSupport(redisTemplate, null, redisTuningProperties);
        nullSupport.write("test-key", "test-value", Duration.ofMinutes(5));
    }

    @Test
    void shouldSkipWrite_whenKeyIsNull() {
        support.write(null, "test-value", Duration.ofMinutes(5));
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
    void shouldHandleRedisWriteFailureGracefully() {
        doAnswer(invocation -> {
            throw new RuntimeException("Redis write failed");
        }).when(valueOps).set(eq("test-key"), anyString(), any(Duration.class));

        support.write("test-key", "test-value", Duration.ofMinutes(5));
    }

    @Test
    void shouldDeleteSingleKey() {
        support.delete("test-key");
    }

    @Test
    void shouldSkipDelete_whenRedisTemplateIsNull() {
        RedisJsonCacheSupport nullSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        nullSupport.delete("test-key");
    }

    @Test
    void shouldHandleDeleteFailureGracefully() {
        StringRedisTemplate errRedis = mock(StringRedisTemplate.class);
        when(errRedis.delete("test-key")).thenThrow(new RuntimeException("Redis delete failed"));
        RedisJsonCacheSupport errSupport = new RedisJsonCacheSupport(errRedis, objectMapper, redisTuningProperties);

        errSupport.delete("test-key");
    }

    @Test
    void shouldDeleteSingleKeyImmediately_whenAfterCommitExecutorIsMissing() {
        support.deleteAfterCommit("test-key");

        verify(redisTemplate).delete("test-key");
    }

    @Test
    void shouldSkipDeleteAfterCommit_whenKeyIsNull() {
        support.deleteAfterCommit((String) null);

        verify(redisTemplate, never()).delete((String) null);
    }

    @Test
    void shouldDeleteSingleKeyAfterCommit_whenTransactionActive() {
        try {
            support.setAfterCommitExecutor(new AfterCommitExecutor());
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            support.deleteAfterCommit("test-key");

            verify(redisTemplate, never()).delete("test-key");
            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCommit();
            verify(redisTemplate).delete("test-key");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
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
    void shouldHandleCollectionDeleteFailureGracefully() {
        List<String> keys = List.of("key1", "key2");
        StringRedisTemplate errRedis = mock(StringRedisTemplate.class);
        when(errRedis.delete(keys)).thenThrow(new RuntimeException("Redis delete failed"));
        RedisJsonCacheSupport errSupport = new RedisJsonCacheSupport(errRedis, objectMapper, redisTuningProperties);

        errSupport.delete(keys);
    }

    @Test
    void shouldSkipCollectionDelete_whenRedisTemplateIsNull() {
        RedisJsonCacheSupport nullSupport = new RedisJsonCacheSupport(null, objectMapper, redisTuningProperties);
        nullSupport.delete(List.of("key1"));
    }

    @Test
    void shouldDeleteCollectionImmediately_whenAfterCommitExecutorIsMissing() {
        List<String> keys = List.of("key1", "key2");

        support.deleteAfterCommit(keys);

        verify(redisTemplate).delete(keys);
    }

    @Test
    void shouldSkipCollectionDeleteAfterCommit_whenKeysAreNullOrEmpty() {
        support.deleteAfterCommit((Collection<String>) null);
        support.deleteAfterCommit(List.of());

        verify(redisTemplate, never()).delete(List.of());
    }

    @Test
    void shouldDeleteCollectionAfterCommit_whenTransactionActive() {
        List<String> keys = List.of("key1", "key2");
        try {
            support.setAfterCommitExecutor(new AfterCommitExecutor());
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            support.deleteAfterCommit(keys);

            verify(redisTemplate, never()).delete(keys);
            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCommit();
            verify(redisTemplate).delete(keys);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
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
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ByteArrayCursor(List.of(
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

        verify(scanRedis).delete(List.of("key:1", "key:2"));
    }

    @Test
    void shouldDeleteByPatternInBatches() {
        RedisTuningProperties batchProps = new RedisTuningProperties();
        batchProps.getScan().setDeleteBatchSize(10);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ByteArrayCursor(List.of(
                "key:1".getBytes(StandardCharsets.UTF_8),
                "key:2".getBytes(StandardCharsets.UTF_8),
                "key:3".getBytes(StandardCharsets.UTF_8),
                "key:4".getBytes(StandardCharsets.UTF_8),
                "key:5".getBytes(StandardCharsets.UTF_8),
                "key:6".getBytes(StandardCharsets.UTF_8),
                "key:7".getBytes(StandardCharsets.UTF_8),
                "key:8".getBytes(StandardCharsets.UTF_8),
                "key:9".getBytes(StandardCharsets.UTF_8),
                "key:10".getBytes(StandardCharsets.UTF_8),
                "key:11".getBytes(StandardCharsets.UTF_8))));
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate scanRedis = mock(StringRedisTemplate.class);
        when(scanRedis.getConnectionFactory()).thenReturn(connectionFactory);
        List<List<String>> deletedBatches = new ArrayList<>();
        when(scanRedis.delete(any(Collection.class))).thenAnswer(invocation -> {
            Collection<?> keys = invocation.getArgument(0);
            deletedBatches.add(keys.stream().map(String::valueOf).toList());
            return (long) keys.size();
        });
        RedisJsonCacheSupport scanSupport = new RedisJsonCacheSupport(scanRedis, objectMapper, batchProps);

        scanSupport.deleteByPattern("key:*");

        assertThat(deletedBatches).containsExactly(
                List.of("key:1", "key:2", "key:3", "key:4", "key:5",
                        "key:6", "key:7", "key:8", "key:9", "key:10"),
                List.of("key:11")
        );
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
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ByteArrayCursor(List.of()));
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
        limitedProps.getScan().setMaxKeys(100);
        limitedProps.getScan().setDeleteBatchSize(10);

        List<byte[]> manyKeys = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            manyKeys.add(("key:" + i).getBytes(StandardCharsets.UTF_8));
        }
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ByteArrayCursor(manyKeys));
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate limitRedis = mock(StringRedisTemplate.class);
        when(limitRedis.getConnectionFactory()).thenReturn(connectionFactory);
        when(limitRedis.delete(any(Collection.class))).thenReturn(1L);
        RedisJsonCacheSupport limitSupport = new RedisJsonCacheSupport(limitRedis, objectMapper, limitedProps);
        limitSupport.deleteByPattern("key:*");

        verify(limitRedis, org.mockito.Mockito.times(10)).delete(any(Collection.class));
    }

    @Test
    void shouldNotDeleteWhenPatternScanFindsNoKeys() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ByteArrayCursor(List.of()));
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        StringRedisTemplate emptyRedis = mock(StringRedisTemplate.class);
        when(emptyRedis.getConnectionFactory()).thenReturn(connectionFactory);
        RedisJsonCacheSupport emptySupport = new RedisJsonCacheSupport(emptyRedis, objectMapper, redisTuningProperties);

        emptySupport.deleteByPattern("key:*");

        verify(emptyRedis, never()).delete(any(Collection.class));
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
