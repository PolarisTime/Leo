package com.leo.erp.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 权限缓存测试。
 *
 * <p>重点验证安全相关属性：RBAC 写入后更换纪元会使既有缓存 key 立即不可达，
 * 从而保证权限收回在下一个请求即生效；以及 Redis 故障时的降级行为。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionCacheServiceTest {

    private static final String EPOCH_KEY = "auth:perm:epoch";
    private static final Long USER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 0L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisJsonCacheSupport cacheSupport;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisTuningProperties redisTuningProperties;

    private PermissionCacheService service;

    @BeforeEach
    void setUp() {
        redisTuningProperties = new RedisTuningProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 真实 AfterCommitExecutor：无活动事务时同步执行，等价于提交后立即失效
        service = new PermissionCacheService(
                redisTemplate, cacheSupport, new AfterCommitExecutor(), redisTuningProperties);
    }

    private void stubCacheReadEmpty() {
        when(cacheSupport.read(anyString(), org.mockito.ArgumentMatchers.<TypeReference<List<String>>>any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void get_shouldUseCurrentEpochInCacheKey() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn("epoch-a");
        when(cacheSupport.read(eq("auth:perm:epoch-a:7:0"),
                org.mockito.ArgumentMatchers.<TypeReference<List<String>>>any()))
                .thenReturn(Optional.of(List.of("roles:read")));

        assertThat(service.get(USER_ID, CREDENTIAL_VERSION)).contains(List.of("roles:read"));
    }

    @Test
    void get_shouldReturnEmptyWhenRedisUnavailable() {
        when(valueOperations.get(EPOCH_KEY)).thenThrow(new RuntimeException("redis down"));

        assertThat(service.get(USER_ID, CREDENTIAL_VERSION)).isEmpty();
    }

    @Test
    void get_shouldSkipNonPositiveUserId() {
        assertThat(service.get(0L, CREDENTIAL_VERSION)).isEmpty();
        assertThat(service.get(null, CREDENTIAL_VERSION)).isEmpty();

        verify(redisTemplate, never()).opsForValue();
    }

    /** epoch 缺失时必须按随机值重建，不能使用固定初值，否则历史 key 会重新可命中。 */
    @Test
    void get_shouldSeedRandomEpochWhenMissing() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(EPOCH_KEY), anyString())).thenReturn(true);
        stubCacheReadEmpty();

        service.get(USER_ID, CREDENTIAL_VERSION);

        ArgumentCaptor<String> seeded = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(EPOCH_KEY), seeded.capture());
        assertThat(seeded.getValue()).isNotBlank();
    }

    @Test
    void get_shouldReuseConcurrentlySeededEpoch() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn(null, "epoch-seeded-by-peer");
        when(valueOperations.setIfAbsent(eq(EPOCH_KEY), anyString())).thenReturn(false);
        stubCacheReadEmpty();

        service.get(USER_ID, CREDENTIAL_VERSION);

        ArgumentCaptor<String> readKey = ArgumentCaptor.forClass(String.class);
        verify(cacheSupport).read(readKey.capture(),
                org.mockito.ArgumentMatchers.<TypeReference<List<String>>>any());
        assertThat(readKey.getValue()).startsWith("auth:perm:epoch-seeded-by-peer:7:0");
    }

    @Test
    void put_shouldWriteWithCurrentEpochAndConfiguredTtl() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn("epoch-a");

        service.put(USER_ID, CREDENTIAL_VERSION, List.of("roles:read"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(cacheSupport).write(key.capture(), value.capture(), ttl.capture());
        assertThat(key.getValue()).isEqualTo("auth:perm:epoch-a:7:0");
        assertThat(value.getValue()).isEqualTo(List.of("roles:read"));
        assertThat(ttl.getValue()).isEqualTo(redisTuningProperties.permissionTtl());
    }

    /** 空权限集合需要缓存，避免无角色用户每次请求都回源查库。 */
    @Test
    void put_shouldCacheEmptyCodeSet() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn("epoch-a");

        service.put(USER_ID, CREDENTIAL_VERSION, List.of());

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(cacheSupport).write(anyString(), value.capture(), any());
        assertThat(value.getValue()).isEqualTo(List.of());
    }

    /**
     * 核心安全属性：更换纪元后，重新读取使用的 key 必须与写入时的 key 不同，
     * 即旧缓存条目立即不可达，权限变更无需枚举受影响用户即可生效。
     */
    @Test
    void invalidateAll_shouldRotateEpochSoExistingEntriesBecomeUnreachable() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn("epoch-old");
        service.put(USER_ID, CREDENTIAL_VERSION, List.of("roles:read"));

        ArgumentCaptor<String> writtenKey = ArgumentCaptor.forClass(String.class);
        verify(cacheSupport).write(writtenKey.capture(), any(), any());

        service.invalidateAll();

        ArgumentCaptor<String> rotated = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(EPOCH_KEY), rotated.capture());
        String newEpoch = rotated.getValue();
        assertThat(newEpoch).isNotBlank().isNotEqualTo("epoch-old");

        when(valueOperations.get(EPOCH_KEY)).thenReturn(newEpoch);
        stubCacheReadEmpty();
        service.get(USER_ID, CREDENTIAL_VERSION);

        ArgumentCaptor<String> readKey = ArgumentCaptor.forClass(String.class);
        verify(cacheSupport).read(readKey.capture(),
                org.mockito.ArgumentMatchers.<TypeReference<List<String>>>any());
        assertThat(readKey.getValue())
                .isNotEqualTo(writtenKey.getValue())
                .isEqualTo("auth:perm:" + newEpoch + ":7:0");
    }

    /** 失效失败不能影响业务写入，TTL 兜底会自然过期。 */
    @Test
    void invalidateAll_shouldSwallowRedisFailure() {
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(eq(EPOCH_KEY), anyString());

        assertThatCode(() -> service.invalidateAll()).doesNotThrowAnyException();
    }

    @Test
    void put_shouldSwallowSerializationFailure() {
        when(valueOperations.get(EPOCH_KEY)).thenReturn("epoch-a");
        doThrow(new RuntimeException("boom")).when(cacheSupport).write(anyString(), any(), any());

        assertThatCode(() -> service.put(USER_ID, CREDENTIAL_VERSION, List.of("roles:read")))
                .doesNotThrowAnyException();
    }
}
