package com.leo.erp.security.permission;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionCacheTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private SetOperations<String, String> setOperations;
    private PermissionCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        setOperations = mock(SetOperations.class);
        RedisTuningProperties properties = new RedisTuningProperties();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        cache = new PermissionCache(redisTemplate, Optional.empty(), properties);
    }

    @Test
    void shouldReturnNullWhenCacheEmpty() {
        when(hashOperations.entries(anyString())).thenReturn(Map.of());

        UserPermissionSnapshot result = cache.read(1L);

        assertThat(result).isNull();
    }

    @Test
    void shouldReadCachedPermissions() {
        Map<Object, Object> permissions = Map.of("material", "read,create");
        Map<Object, Object> scopes = Map.of("material:read", "self");

        when(hashOperations.entries("leo:resource-perm:1")).thenReturn(permissions);
        when(hashOperations.entries("leo:resource-scope:1")).thenReturn(scopes);

        UserPermissionSnapshot result = cache.read(1L);

        assertThat(result).isNotNull();
        assertThat(result.permissionMap()).containsKey("material");
        assertThat(result.permissionMap().get("material")).containsExactlyInAnyOrder("read", "create");
    }

    @Test
    void shouldWritePermissionsToCache() {
        Map<String, Set<String>> permissionMap = Map.of("material", Set.of("read", "create"));
        Map<String, String> dataScope = Map.of("material:read", "self");
        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap, dataScope);

        cache.write(1L, snapshot);

        verify(hashOperations, atLeastOnce()).putAll(anyString(), any(Map.class));
        verify(redisTemplate, atLeastOnce()).expire(anyString(), anyLong(), any());
    }

    @Test
    void shouldEvictUserCache() {
        cache.evict(1L);

        verify(redisTemplate).delete(any(Collection.class));
    }

    @Test
    void shouldSplitActions() {
        Set<String> actions = cache.splitActions("read,create,update");

        assertThat(actions).containsExactlyInAnyOrder("read", "create", "update");
    }

    @Test
    void shouldReturnEmptySetForBlankActions() {
        assertThat(cache.splitActions("")).isEmpty();
        assertThat(cache.splitActions(null)).isEmpty();
        assertThat(cache.splitActions("   ")).isEmpty();
    }

    @Test
    void shouldFilterBlankActions() {
        Set<String> actions = cache.splitActions("read,,create,");

        assertThat(actions).containsExactlyInAnyOrder("read", "create");
    }
}
