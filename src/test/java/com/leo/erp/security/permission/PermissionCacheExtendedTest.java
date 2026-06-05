package com.leo.erp.security.permission;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void shouldSkipEvictMetadataWhenCacheSupportNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisTuningProperties props = new RedisTuningProperties();
        PermissionCache cache = new PermissionCache(redisTemplate, Optional.empty(), props);

        cache.evictMetadata("some-key");

        verify(redisTemplate, never()).delete(anyString());
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
}
