package com.leo.erp.security.rbac;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.security.rbac.repository.SysUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 权限缓存「按范围失效 + 单飞」的行为测试（2026-09-30 报告 P1/P2 项）。
 *
 * <p>被测的三条性质：</p>
 * <ol>
 *   <li><b>按用户失效</b>：只让目标用户的缓存不可命中，其它用户的缓存保持可用
 *       ——原实现换全局纪元，改一个角色会清空所有人的缓存。</li>
 *   <li><b>按角色失效</b>：先反查该角色下用户再逐个失效；影响面过大或反查失败时
 *       退化为全局失效（宁可多失效，不可漏失效）。</li>
 *   <li><b>单飞</b>：同一用户的并发回源只查一次库
 *       （实测轮换后 50 并发会回源 6 次，真实瞬时并发下更高）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionCacheScopedInvalidationTest {

    private static final long USER_ID = 7L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SysUserRoleRepository userRoleRepository;

    /** 用内存实现替代真实 Redis：缓存必须真的能存住，单飞断言才有意义。 */
    private final Map<String, Object> cacheStore = new ConcurrentHashMap<>();
    private final Map<String, String> kvStore = new ConcurrentHashMap<>();

    private RedisJsonCacheSupport cacheSupport;

    private PermissionCacheService service;

    @BeforeEach
    void setUp() {
        RedisTuningProperties redisTuningProperties = new RedisTuningProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> kvStore.get(inv.getArgument(0)));
        // 两级纪元经 MGET 一条命令取回（生产路径）：必须同样读内存替身，
        // 否则用户级纪元对读路径不可见，按范围失效的断言会静默失效
        when(valueOperations.multiGet(any())).thenAnswer(inv -> {
            java.util.Collection<String> keys = inv.getArgument(0);
            return keys == null ? java.util.List.of() : keys.stream().map(kvStore::get).toList();
        });
        when(valueOperations.setIfAbsent(anyString(), anyString()))
                .thenAnswer(inv -> kvStore.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        // 纪元轮换依赖 set 写入：不 stub 的话失效不会真正生效（测试会静默通过成「命中」，反而掩盖问题）
        org.mockito.Mockito.doAnswer(inv -> {
            kvStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());

        cacheSupport = org.mockito.Mockito.mock(RedisJsonCacheSupport.class);
        when(cacheSupport.read(anyString(), any(TypeReference.class)))
                .thenAnswer(inv -> Optional.ofNullable(cacheStore.get(inv.getArgument(0))));
        org.mockito.Mockito.doAnswer(inv -> {
            cacheStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(cacheSupport).write(anyString(), any(), any(Duration.class));

        service = new PermissionCacheService(
                redisTemplate, cacheSupport, new AfterCommitExecutor(), redisTuningProperties,
                userRoleRepository, null);
    }

    @Test
    void invalidateUser_onlyAffectsThatUser() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));
        service.getOrLoad(9L, 0L, () -> List.of("roles:read"));
        assertThat(nonEmptyKeysFor(USER_ID)).isTrue();
        assertThat(nonEmptyKeysFor(9L)).isTrue();

        service.invalidateUser(USER_ID);

        // 目标用户：旧 key 不可达；另一个用户：缓存仍然命中（不必回源）
        assertThat(cacheStore.keySet().stream().filter(key -> key.contains(":7:")).count()).isPositive();
        AtomicInteger loads = new AtomicInteger();
        List<String> loaded = service.getOrLoad(9L, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(loaded).containsExactly("roles:read");
        assertThat(loads.get()).isZero();

        AtomicInteger userReloads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            userReloads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(userReloads.get()).isEqualTo(1);
    }

    @Test
    void invalidateUsersByRole_rotatesOnlyAffectedUsers() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));
        service.getOrLoad(9L, 0L, () -> List.of("roles:read"));
        when(userRoleRepository.findUserIdsByRoleIdIn(any())).thenReturn(List.of(USER_ID));

        service.invalidateUsersByRole(List.of(5L));

        AtomicInteger loads = new AtomicInteger();
        service.getOrLoad(9L, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(loads.get()).isZero();
        AtomicInteger reloads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            reloads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(reloads.get()).isEqualTo(1);
    }

    @Test
    void invalidateUsersByRole_fallsBackToGlobalWhenLookupFails() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));
        service.getOrLoad(9L, 0L, () -> List.of("roles:read"));
        when(userRoleRepository.findUserIdsByRoleIdIn(any()))
                .thenThrow(new IllegalStateException("db down"));

        service.invalidateUsersByRole(List.of(5L));

        // 反查失败必须退化为全局失效：两个用户都应重新回源
        AtomicInteger loads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        service.getOrLoad(9L, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(loads.get()).isEqualTo(2);
    }

    /**
     * 回归（C 包缺陷修复）：被按范围失效过一次的用户已经拥有用户级纪元，
     * 此后「全局兜底失效」（反查失败/影响面过大）必须仍然让它失效。
     * 早期实现的 key 只带用户级纪元，全局轮换对该用户静默无效 = 权限收不回来。
     */
    @Test
    void invalidateAll_alsoInvalidatesUsersWhoAlreadyHaveUserEpoch() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));
        service.invalidateUser(USER_ID);                                  // 用户 7 获得用户级纪元
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));      // 以两级纪元重新写入
        service.getOrLoad(9L, 0L, () -> List.of("roles:read"));

        service.invalidateAll();                                          // 全局兜底

        AtomicInteger loads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        service.getOrLoad(9L, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(loads.get()).isEqualTo(2);
    }

    /**
     * 大面积变更（影响用户数超过阈值）退化为全局失效时，同样必须覆盖已有用户级纪元的用户。
     */
    @Test
    void invalidateUsersByRole_tooManyAffectedUsers_fallsBackToGlobalForEveryone() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));
        service.invalidateUser(USER_ID);
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));

        java.util.List<Long> manyUsers = new java.util.ArrayList<>();
        for (long id = 100; id <= 600; id++) {
            manyUsers.add(id);                                            // 501 人 > 阈值 500
        }
        when(userRoleRepository.findUserIdsByRoleIdIn(any())).thenReturn(manyUsers);

        service.invalidateUsersByRole(List.of(5L));

        AtomicInteger loads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read");
        });
        assertThat(loads.get()).isEqualTo(1);
    }

    /**
     * 缓存指标：命中/未命中/回源三个计数器必须可区分，否则无法在监控里判断缓存是否真的生效
     * （权限缓存走裸 Redis，不产生 Spring Cache 的 cache_* 指标）。
     */
    @Test
    void metrics_countHitMissAndLoad() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(meterProvider.getIfAvailable()).thenReturn(registry);
        PermissionCacheService counted = new PermissionCacheService(
                redisTemplate, cacheSupport, new AfterCommitExecutor(), new RedisTuningProperties(),
                userRoleRepository, meterProvider);

        counted.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));      // miss + load
        counted.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));      // hit

        assertThat(registry.get("leo.cache.permission").tag("outcome", "miss").counter().count()).isEqualTo(1);
        assertThat(registry.get("leo.cache.permission").tag("outcome", "load").counter().count()).isEqualTo(1);
        assertThat(registry.get("leo.cache.permission").tag("outcome", "hit").counter().count()).isEqualTo(1);
    }

    @Test
    void concurrentLoadsForSameUser_queryDatabaseOnlyOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<List<String>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await(2, TimeUnit.SECONDS);
                    return service.getOrLoad(USER_ID, 0L, () -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(80L);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                        return List.of("roles:read", "sales-orders:read");
                    });
                }));
            }
            start.countDown();
            for (var future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).containsExactly("roles:read", "sales-orders:read");
            }
        } finally {
            pool.shutdownNow();
        }
        // 单飞生效：8 个并发请求只回源一次
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void getOrLoad_emptyResultIsCachedToAvoidRepeatedLookups() {
        AtomicInteger loads = new AtomicInteger();
        service.getOrLoad(USER_ID, 0L, () -> {
            loads.incrementAndGet();
            return List.of();
        });
        service.getOrLoad(USER_ID, 0L, () -> {
            loads.incrementAndGet();
            return List.of();
        });
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void credentialVersionChange_bypassesCacheEntry() {
        service.getOrLoad(USER_ID, 0L, () -> List.of("roles:read"));

        AtomicInteger loads = new AtomicInteger();
        List<String> afterRotation = service.getOrLoad(USER_ID, 1L, () -> {
            loads.incrementAndGet();
            return List.of("roles:read", "roles:write");
        });

        assertThat(loads.get()).isEqualTo(1);
        assertThat(afterRotation).containsExactly("roles:read", "roles:write");
    }

    private boolean nonEmptyKeysFor(long userId) {
        return cacheStore.keySet().stream().anyMatch(key -> key.contains(":" + userId + ":"));
    }
}
