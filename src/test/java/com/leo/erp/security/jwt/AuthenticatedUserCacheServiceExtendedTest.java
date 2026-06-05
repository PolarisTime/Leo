package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedUserCacheServiceExtendedTest {

    @Test
    void shouldReturnEmptyWhenUserIdIsNull() {
        AuthenticatedUserCacheService service = createService();

        assertThat(service.getActivePrincipal(null)).isEmpty();
    }

    @Test
    void shouldReturnCachedPrincipalWhenCacheHit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:user:snapshot:101")).thenReturn(
                "{\"userId\":101,\"loginName\":\"admin\",\"authorities\":[\"ROLE_ADMIN\"],\"totpEnabled\":false,\"forceTotpSetup\":false}"
        );

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                mock(UserAccountRepository.class), mock(UserRoleBindingService.class),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> result = service.getActivePrincipal(101L);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldLoadFromDbWhenCacheMiss() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.get("auth:user:snapshot:101")).thenReturn(null);

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId(101L);
        user.setLoginName("admin");
        user.setStatus(UserStatus.NORMAL);
        when(userRepo.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(user));

        UserRoleBindingService roleService = mock(UserRoleBindingService.class);
        when(roleService.resolveRolesForUser(101L)).thenReturn(java.util.List.of());

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                userRepo, roleService, new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> result = service.getActivePrincipal(101L);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("admin");
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:user:snapshot:999")).thenReturn(null);

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                userRepo, mock(UserRoleBindingService.class), new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(999L)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserNotNormal() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:user:snapshot:101")).thenReturn(null);

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UserAccount user = new UserAccount();
        user.setId(101L);
        user.setLoginName("admin");
        user.setStatus(UserStatus.DISABLED);
        when(userRepo.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(user));

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                userRepo, mock(UserRoleBindingService.class), new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(101L)).isEmpty();
    }

    @Test
    void shouldDeleteCacheAndReturnEmptyWhenJsonMalformed() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:user:snapshot:101")).thenReturn("not-json");

        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        when(userRepo.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.empty());

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                userRepo, mock(UserRoleBindingService.class), new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(101L)).isEmpty();
        verify(redisTemplate).delete("auth:user:snapshot:101");
    }

    @Test
    void shouldEvictCacheKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                mock(UserAccountRepository.class), mock(UserRoleBindingService.class),
                new RedisTuningProperties()
        );

        service.evict(101L);

        verify(redisTemplate).delete("auth:user:snapshot:101");
        verify(setOps).remove("auth:user:snapshot:index", "101");
    }

    @Test
    void shouldSkipEvictWhenUserIdNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                mock(UserAccountRepository.class), mock(UserRoleBindingService.class),
                new RedisTuningProperties()
        );

        service.evict(null);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldFallbackToScanWhenIndexKeyMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("auth:user:snapshot:index")).thenReturn(false);
        when(redisTemplate.getConnectionFactory()).thenReturn(null);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate, new ObjectMapper(),
                mock(UserAccountRepository.class), mock(UserRoleBindingService.class),
                new RedisTuningProperties()
        );

        service.evictAll();
    }

    private AuthenticatedUserCacheService createService() {
        return new AuthenticatedUserCacheService(
                mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(UserAccountRepository.class), mock(UserRoleBindingService.class),
                new RedisTuningProperties()
        );
    }
}
