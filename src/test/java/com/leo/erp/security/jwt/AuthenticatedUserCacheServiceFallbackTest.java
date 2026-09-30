package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.api.AuthenticationAccountQuery;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.config.AuthFallbackProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证主体缓存「Redis 不可用」时的行为测试（2026-09-30 报告第 4 项 P0 的回归保护）。
 *
 * <p>被测的关键取舍：默认 fail-closed（拿不到就抛 503 语义的异常），
 * 只有在显式开启有界降级窗口后，才允许用进程内快照继续放行，且窗口有 TTL 上限。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticatedUserCacheServiceFallbackTest {

    private static final String VALID_SNAPSHOT = "{\"userId\":42,\"loginName\":\"perf_user\",\"credentialVersion\":0}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthenticationAccountQuery authenticationAccountQuery;

    private RedisTuningProperties redisTuningProperties;

    private AuthFallbackProperties fallbackProperties;

    private AuthenticatedUserCacheService service;

    @BeforeEach
    void setUp() {
        redisTuningProperties = org.mockito.Mockito.mock(RedisTuningProperties.class);
        when(redisTuningProperties.authUserTtl()).thenReturn(Duration.ofMinutes(10));
        when(redisTuningProperties.authUserIndexTtl()).thenReturn(Duration.ofDays(1));
        when(redisTuningProperties.withTtlJitter(any())).thenReturn(Duration.ofMinutes(10));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        fallbackProperties = new AuthFallbackProperties();
        service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                authenticationAccountQuery,
                redisTuningProperties,
                fallbackProperties
        );
    }

    @Test
    void cacheHit_returnsPrincipalAndNeverTouchesDatabase() {
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(30);
        when(valueOperations.get(anyString())).thenReturn(VALID_SNAPSHOT);

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(42L, 0L);

        assertThat(principal).isPresent();
        assertThat(principal.get().id()).isEqualTo(42L);
        verify(authenticationAccountQuery, never()).findActiveById(any());
        assertThat(service.fallbackSnapshotCount()).isEqualTo(1);
    }

    @Test
    void redisDownWithWindowDisabled_failsClosed() {
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        assertThatThrownBy(() -> service.getActivePrincipal(42L, 0L))
                .isInstanceOf(AuthenticationInfrastructureException.class)
                .hasMessageContaining("Redis");
    }

    @Test
    void redisDownWithWindowEnabled_servesLastKnownSnapshot() {
        // 先开窗口并成功读一次，记住快照
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(30);
        when(valueOperations.get(anyString())).thenReturn(VALID_SNAPSHOT);
        assertThat(service.getActivePrincipal(42L, 0L)).isPresent();

        // Redis 随后不可用
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(42L, 0L);

        assertThat(principal).isPresent();
        assertThat(principal.get().username()).isEqualTo("perf_user");
    }

    @Test
    void snapshotBeyondTtl_isNotUsedEvenWhenWindowEnabled() throws InterruptedException {
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(1);
        when(valueOperations.get(anyString())).thenReturn(VALID_SNAPSHOT);
        assertThat(service.getActivePrincipal(42L, 0L)).isPresent();

        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));
        Thread.sleep(1_100L);

        assertThatThrownBy(() -> service.getActivePrincipal(42L, 0L))
                .isInstanceOf(AuthenticationInfrastructureException.class);
    }

    @Test
    void credentialVersionMismatch_doesNotSatisfyFallback() {
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(30);
        when(valueOperations.get(anyString())).thenReturn(VALID_SNAPSHOT);
        assertThat(service.getActivePrincipal(42L, 0L)).isPresent();

        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis connection failure"));

        // 令牌里的凭证版本与快照不一致（例如刚改过密码）→ 不能拿旧快照放行
        assertThatThrownBy(() -> service.getActivePrincipal(42L, 7L))
                .isInstanceOf(AuthenticationInfrastructureException.class);
    }

    @Test
    void cacheWriteFailure_stillReturnsDatabaseResult() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(authenticationAccountQuery.findActiveById(42L)).thenReturn(Optional.of(
                new AuthenticationAccountQuery.AuthenticatedAccountSnapshot(42L, "perf_user", 0L)));
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("Redis write failure"));

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(42L, 0L);

        // DB 是权威来源：回填缓存失败不应让登录态失效
        assertThat(principal).isPresent();
        assertThat(principal.get().id()).isEqualTo(42L);
    }

    @Test
    void fallbackCacheIsBoundedByMaxEntries() {
        fallbackProperties.setEnabled(true);
        fallbackProperties.setTtlSeconds(30);
        fallbackProperties.setMaxEntries(2);
        when(valueOperations.get(anyString())).thenReturn(VALID_SNAPSHOT);

        for (long userId = 1L; userId <= 5L; userId++) {
            when(valueOperations.get(anyString()))
                    .thenReturn("{\"userId\":" + userId + ",\"loginName\":\"u" + userId + "\",\"credentialVersion\":0}");
            service.getActivePrincipal(userId, 0L);
        }

        assertThat(service.fallbackSnapshotCount()).isLessThanOrEqualTo(2);
    }
}
