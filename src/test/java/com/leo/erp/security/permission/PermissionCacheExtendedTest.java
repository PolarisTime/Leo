package com.leo.erp.security.permission;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionCacheExtendedTest {

    @Test
    void shouldReturnNullWhenScopeCacheIsEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("leo:resource-perm:1")).thenReturn(Map.of("purchase-order", "read"));
        when(hashOps.entries("leo:resource-scope:1")).thenReturn(Collections.emptyMap());

        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        UserPermissionSnapshot result = cache.read(1L);

        assertThat(result).isNull();
    }

    @Test
    void shouldNormalizeCachedPermissionAndScopeEntries() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("leo:resource-perm:7")).thenReturn(Map.of(" Material ", " view, edit, "));
        when(hashOps.entries("leo:resource-scope:7")).thenReturn(Map.of(
                " Material : view ", "全部数据",
                "invalid", "self"
        ));

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), new RedisTuningProperties());

        UserPermissionSnapshot result = cache.read(7L);

        assertThat(result.permissionMap()).containsKey("material");
        assertThat(result.permissionMap().get("material")).containsExactly("read", "update");
        assertThat(result.dataScopeByPermission())
                .containsEntry("material:read", "all")
                .doesNotContainKey("invalid");
    }

    @Test
    void shouldSkipWriteWhenPermissionMapIsEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(Map.of(), Map.of());
        cache.write(1L, snapshot);

        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void shouldWriteDataScopeWhenPresent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(
                Map.of("purchase-order", Set.of("read", "update")),
                Map.of("purchase-order:read", "self")
        );
        cache.write(1L, snapshot);

        verify(hashOps).putAll(org.mockito.ArgumentMatchers.eq("leo:resource-perm:1"), org.mockito.ArgumentMatchers.any(Map.class));
        verify(hashOps).putAll("leo:resource-scope:1", Map.of("purchase-order:read", "self"));
    }

    @Test
    void shouldWritePermissionIndexAndSkipScopeHashWhenScopeIsEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), new RedisTuningProperties());

        cache.write(9L, new UserPermissionSnapshot(Map.of("material", Set.of("read")), Map.of()));

        verify(hashOps).putAll(eq("leo:resource-perm:9"), any(Map.class));
        verify(hashOps, never()).putAll(eq("leo:resource-scope:9"), any(Map.class));
        verify(setOps).add("leo:resource-perm:index:users", "9");
    }

    @Test
    void shouldEvictUserCacheKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        cache.evict(1L);

        verify(redisTemplate).delete(java.util.List.of("leo:resource-perm:1", "leo:resource-scope:1"));
        verify(setOps).remove("leo:resource-perm:index:users", "1");
    }

    @Test
    void shouldEvictAllIndexedUsersWithoutFallbackScan() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(true);
        Cursor<String> cursor = stringCursor("1", "2");
        when(setOps.scan(eq("leo:resource-perm:index:users"), any(ScanOptions.class)))
                .thenReturn(cursor);

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), new RedisTuningProperties());

        cache.evictAll();

        verify(redisTemplate).delete(List.of(
                "leo:resource-perm:1",
                "leo:resource-scope:1",
                "leo:resource-perm:2",
                "leo:resource-scope:2"
        ));
        verify(redisTemplate).delete("leo:resource-perm:index:users");
        verify(redisTemplate, never()).getConnectionFactory();
    }

    @Test
    void shouldFallbackToPatternEvictionWhenIndexedEvictionFailsAndCacheSupportExists() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(true);
        when(setOps.scan(eq("leo:resource-perm:index:users"), any(ScanOptions.class)))
                .thenThrow(new IllegalStateException("scan failed"));
        com.leo.erp.common.support.RedisJsonCacheSupport cacheSupport =
                mock(com.leo.erp.common.support.RedisJsonCacheSupport.class);

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.of(cacheSupport), new RedisTuningProperties());

        cache.evictAll();

        verify(cacheSupport).deleteByPattern("leo:resource-perm:*");
        verify(cacheSupport).deleteByPattern("leo:resource-scope:*");
    }

    @Test
    void shouldReturnWhenFallbackScanHasNoConnectionFactoryAndCacheSupportOptionalIsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(false);
        when(redisTemplate.getConnectionFactory()).thenReturn(null);

        PermissionCache cache = new PermissionCache(redisTemplate, null, new RedisTuningProperties());

        cache.evictAll();

        verify(redisTemplate).getConnectionFactory();
        verify(redisTemplate, never()).delete(any(Collection.class));
    }

    @Test
    void shouldEvictIndexedUsersWhenDeleteBatchThresholdIsReached() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(true);
        Cursor<String> cursor = stringCursor("1", "2", "3", "4", "5");
        when(setOps.scan(eq("leo:resource-perm:index:users"), any(ScanOptions.class)))
                .thenReturn(cursor);
        List<List<String>> deletedBatches = new ArrayList<>();
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            deletedBatches.add(List.copyOf(invocation.getArgument(0, Collection.class)));
            return 0L;
        });

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), smallScanTuningProperties());

        cache.evictAll();

        assertThat(deletedBatches).containsExactly(List.of(
                "leo:resource-perm:1",
                "leo:resource-scope:1",
                "leo:resource-perm:2",
                "leo:resource-scope:2",
                "leo:resource-perm:3",
                "leo:resource-scope:3",
                "leo:resource-perm:4",
                "leo:resource-scope:4",
                "leo:resource-perm:5",
                "leo:resource-scope:5"
        ));
        verify(redisTemplate).delete("leo:resource-perm:index:users");
        verify(redisTemplate, never()).getConnectionFactory();
    }

    @Test
    void shouldEvictOnlyIndexWhenIndexedUserCursorIsEmpty() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(true);
        Cursor<String> cursor = stringCursor();
        when(setOps.scan(eq("leo:resource-perm:index:users"), any(ScanOptions.class)))
                .thenReturn(cursor);

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), smallScanTuningProperties());

        cache.evictAll();

        verify(redisTemplate, never()).delete(any(Collection.class));
        verify(redisTemplate).delete("leo:resource-perm:index:users");
        verify(redisTemplate, never()).getConnectionFactory();
    }

    @Test
    void shouldScanAndDeletePermissionKeysWhenIndexAndCacheSupportAreUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(false);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        Cursor<byte[]> permissionCursor = byteCursor("leo:resource-perm:1", "leo:resource-perm:2");
        Cursor<byte[]> scopeCursor = byteCursor("leo:resource-scope:1");
        when(connection.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            ScanOptions options = invocation.getArgument(0, ScanOptions.class);
            return switch (options.getPattern()) {
                case "leo:resource-perm:*" -> permissionCursor;
                case "leo:resource-scope:*" -> scopeCursor;
                default -> throw new AssertionError("Unexpected scan pattern: " + options.getPattern());
            };
        });
        List<List<String>> deletedBatches = new ArrayList<>();
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            deletedBatches.add(List.copyOf(invocation.getArgument(0, Collection.class)));
            return 0L;
        });

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), new RedisTuningProperties());

        cache.evictAll();

        assertThat(deletedBatches).containsExactly(
                List.of("leo:resource-perm:1", "leo:resource-perm:2"),
                List.of("leo:resource-scope:1")
        );
        verify(connection).close();
    }

    @Test
    void shouldStopScanDeletionWhenMaxScanKeysReached() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redisTemplate.hasKey("leo:resource-perm:index:users")).thenReturn(false);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        Cursor<byte[]> permissionCursor = byteCursor(numberedKeys("leo:resource-perm:", 1, 101));
        Cursor<byte[]> scopeCursor = byteCursor();
        when(connection.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            ScanOptions options = invocation.getArgument(0, ScanOptions.class);
            return switch (options.getPattern()) {
                case "leo:resource-perm:*" -> permissionCursor;
                case "leo:resource-scope:*" -> scopeCursor;
                default -> throw new AssertionError("Unexpected scan pattern: " + options.getPattern());
            };
        });
        List<List<String>> deletedBatches = new ArrayList<>();
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            deletedBatches.add(List.copyOf(invocation.getArgument(0, Collection.class)));
            return 0L;
        });

        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), smallScanTuningProperties());

        cache.evictAll();

        assertThat(deletedBatches).hasSize(10);
        assertThat(deletedBatches.get(0)).containsExactlyElementsOf(numberedKeys("leo:resource-perm:", 1, 10));
        assertThat(deletedBatches.get(9)).containsExactlyElementsOf(numberedKeys("leo:resource-perm:", 91, 100));
        assertThat(deletedBatches).allSatisfy(batch -> assertThat(batch).doesNotContain("leo:resource-perm:101"));
        verify(connection).close();
    }

    @Test
    void shouldReturnEmptySetForBlankActions() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        assertThat(cache.splitActions(null)).isEmpty();
        assertThat(cache.splitActions("")).isEmpty();
        assertThat(cache.splitActions("   ")).isEmpty();
    }

    @Test
    void shouldSplitAndNormalizeActions() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        Set<String> actions = cache.splitActions("read, update, , manage_permissions");

        assertThat(actions).containsExactly("read", "update", "manage_permissions");
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> stringCursor(String... values) {
        Cursor<String> cursor = mock(Cursor.class);
        AtomicInteger index = new AtomicInteger();
        when(cursor.hasNext()).thenAnswer(invocation -> index.get() < values.length);
        when(cursor.next()).thenAnswer(invocation -> values[index.getAndIncrement()]);
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private Cursor<byte[]> byteCursor(String... keys) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        AtomicInteger index = new AtomicInteger();
        when(cursor.hasNext()).thenAnswer(invocation -> index.get() < keys.length);
        when(cursor.next()).thenAnswer(invocation ->
                keys[index.getAndIncrement()].getBytes(StandardCharsets.UTF_8));
        return cursor;
    }

    private Cursor<byte[]> byteCursor(List<String> keys) {
        return byteCursor(keys.toArray(new String[0]));
    }

    private RedisTuningProperties smallScanTuningProperties() {
        RedisTuningProperties properties = new RedisTuningProperties();
        properties.getScan().setBatchSize(10);
        properties.getScan().setDeleteBatchSize(10);
        properties.getScan().setMaxKeys(100);
        return properties;
    }

    private List<String> numberedKeys(String prefix, int startInclusive, int endInclusive) {
        List<String> keys = new ArrayList<>();
        for (int index = startInclusive; index <= endInclusive; index++) {
            keys.add(prefix + index);
        }
        return keys;
    }
}
