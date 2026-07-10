package com.leo.erp.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserCacheServiceTest {

    @Test
    void shouldReturnPrincipalFromCacheWithoutQueryingRepository() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(Map.of(
                "auth:user:snapshot:1",
                "{\"userId\":1,\"loginName\":\"leo\",\"authorities\":[\"ROLE_ADMIN\"],\"totpEnabled\":true,\"forceTotpSetup\":false}"
        ));
        AtomicBoolean repositoryQueried = new AtomicBoolean(false);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> {
                    repositoryQueried.set(true);
                    return Optional.empty();
                }),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(1L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getUsername()).isEqualTo("leo");
        assertThat(principal.orElseThrow().getPassword()).isEmpty();
        assertThat(principal.orElseThrow().totpEnabled()).isTrue();
        assertThat(principal.orElseThrow().forceTotpSetup()).isFalse();
        assertThat(principal.orElseThrow().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
        assertThat(repositoryQueried.get()).isFalse();
    }

    @Test
    void shouldLoadAndCacheActiveUserWhenCacheMissed() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        UserAccount user = new UserAccount();
        user.setId(2L);
        user.setLoginName("ops");
        user.setStatus(UserStatus.NORMAL);
        user.setCredentialVersion(4L);
        user.setTotpEnabled(Boolean.FALSE);
        user.setRequireTotpSetup(Boolean.TRUE);

        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("系统管理员");
        role.setStatus("正常");

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of(role)),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(2L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getPassword()).isEmpty();
        assertThat(principal.orElseThrow().credentialVersion()).isEqualTo(4L);
        assertThat(principal.orElseThrow().totpEnabled()).isFalse();
        assertThat(principal.orElseThrow().forceTotpSetup()).isTrue();
        assertThat(redisTemplate.lastSetKey).isEqualTo("auth:user:snapshot:2");
        assertThat(redisTemplate.lastSetTtl).isGreaterThanOrEqualTo(Duration.ofMinutes(2));
        assertThat(redisTemplate.values.get("auth:user:snapshot:2")).contains("\"forceTotpSetup\":true");
        assertThat(redisTemplate.values.get("auth:user:snapshot:2")).contains("\"credentialVersion\":4");
    }

    @Test
    void shouldLoadAndCacheActiveUserWhenCachedValueIsBlank() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>(
                Map.of("auth:user:snapshot:12", "  ")
        ));
        UserAccount user = new UserAccount();
        user.setId(12L);
        user.setLoginName("blank-cache");
        user.setStatus(UserStatus.NORMAL);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(12L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getUsername()).isEqualTo("blank-cache");
        assertThat(redisTemplate.lastSetKey).isEqualTo("auth:user:snapshot:12");
    }

    @Test
    void shouldReturnEmptyWhenUserIdIsNull() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserNotInRepository() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(999L)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserStatusIsNotNormal() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        UserAccount user = new UserAccount();
        user.setId(5L);
        user.setLoginName("disabled");
        user.setStatus(UserStatus.DISABLED);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        assertThat(service.getActivePrincipal(5L)).isEmpty();
    }

    @Test
    void shouldDeleteCacheAndFallbackWhenCachedJsonIsMalformed() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>(
                Map.of("auth:user:snapshot:10", "not-valid-json{{{")
        ));
        UserAccount user = new UserAccount();
        user.setId(10L);
        user.setLoginName("valid");
        user.setStatus(UserStatus.NORMAL);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(10L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().getUsername()).isEqualTo("valid");
        assertThat(redisTemplate.deletedKeys).contains("auth:user:snapshot:10");
    }

    @Test
    void shouldDeleteCacheEntryOnEvict() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>(
                Map.of("auth:user:snapshot:7", "{\"userId\":7}")
        ));
        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evict(7L);

        assertThat(redisTemplate.values).doesNotContainKey("auth:user:snapshot:7");
    }

    @Test
    void shouldSkipEvictWhenUserIdIsNull() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evict(null);
        assertThat(redisTemplate.deletedKeys).isEmpty();
    }

    @Test
    void shouldEvictAllWhenIndexKeyMissing() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        redisTemplate.hasIndexKey = false;

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();
        assertThat(redisTemplate.scanFallbackTriggered).isTrue();
    }

    @Test
    void shouldEvictAllUsingIndexScanAndSkipInvalidUserIds() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>(
                Map.of(
                        "auth:user:snapshot:1", "one",
                        "auth:user:snapshot:2", "two",
                        "auth:user:snapshot:bad", "bad"
                )
        ));
        redisTemplate.indexMembers = List.of("1", "bad", "2");

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();

        assertThat(redisTemplate.deletedKeys)
                .contains("auth:user:snapshot:1", "auth:user:snapshot:2", "auth:user:snapshot:index")
                .doesNotContain("auth:user:snapshot:bad");
        assertThat(redisTemplate.values)
                .doesNotContainKeys("auth:user:snapshot:1", "auth:user:snapshot:2")
                .containsKey("auth:user:snapshot:bad");
    }

    @Test
    void shouldDeleteIndexedKeysInBatches() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        List<String> members = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            members.add(String.valueOf(i));
            redisTemplate.values.put("auth:user:snapshot:" + i, "cached");
        }
        redisTemplate.indexMembers = members;
        RedisTuningProperties tuning = new RedisTuningProperties();
        tuning.getScan().setDeleteBatchSize(10);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                tuning
        );

        service.evictAll();

        assertThat(redisTemplate.deletedKeys).contains("auth:user:snapshot:0", "auth:user:snapshot:9");
    }

    @Test
    void shouldEvictAllByScanFallbackWhenIndexMissingAndConnectionExists() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>(
                Map.of(
                        "auth:user:snapshot:3", "three",
                        "auth:user:snapshot:4", "four"
                )
        ));
        redisTemplate.hasIndexKey = false;
        redisTemplate.scanKeys = List.of("auth:user:snapshot:3", "auth:user:snapshot:4");

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();

        assertThat(redisTemplate.deletedKeys).contains("auth:user:snapshot:3", "auth:user:snapshot:4");
        assertThat(redisTemplate.connectionClosed).isTrue();
    }

    @Test
    void shouldStopScanFallbackWhenMaxScanLimitIsReached() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        redisTemplate.hasIndexKey = false;
        List<String> scanKeys = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            scanKeys.add("auth:user:snapshot:" + i);
        }
        redisTemplate.scanKeys = scanKeys;
        RedisTuningProperties tuning = new RedisTuningProperties();
        tuning.getScan().setDeleteBatchSize(10);
        tuning.getScan().setMaxKeys(100);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                tuning
        );

        service.evictAll();

        assertThat(redisTemplate.deletedKeys).hasSize(100);
        assertThat(redisTemplate.connectionClosed).isTrue();
    }

    @Test
    void shouldCloseConnectionWhenScanFallbackFails() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        redisTemplate.hasIndexKey = false;
        redisTemplate.throwOnScan = true;

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();

        assertThat(redisTemplate.connectionClosed).isTrue();
    }

    @Test
    void shouldIgnoreConnectionCloseFailureAfterScanFallback() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        redisTemplate.hasIndexKey = false;
        redisTemplate.throwOnClose = true;

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();

        assertThat(redisTemplate.connectionClosed).isTrue();
    }

    @Test
    void shouldFailWhenCachedPrincipalSerializationFails() throws Exception {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("broken") {
                });
        UserAccount user = new UserAccount();
        user.setId(11L);
        user.setLoginName("broken");
        user.setStatus(UserStatus.NORMAL);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                objectMapper,
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getActivePrincipal(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("认证用户缓存序列化失败");
    }

    @Test
    void shouldNotCrashWhenEvictAllWithNullConnectionFactory() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        redisTemplate.hasIndexKey = false;
        redisTemplate.nullConnectionFactory = true;

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.empty()),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        service.evictAll();
    }

    @Test
    void shouldSetTotpEnabledTrueWhenUserHasTotpEnabled() {
        StubRedisTemplate redisTemplate = new StubRedisTemplate(new HashMap<>());
        UserAccount user = new UserAccount();
        user.setId(6L);
        user.setLoginName("totpuser");
        user.setStatus(UserStatus.NORMAL);
        user.setTotpEnabled(Boolean.TRUE);
        user.setRequireTotpSetup(Boolean.FALSE);

        AuthenticatedUserCacheService service = new AuthenticatedUserCacheService(
                redisTemplate,
                new ObjectMapper(),
                repository(userId -> Optional.of(user)),
                roleBindingService(List.of()),
                new RedisTuningProperties()
        );

        Optional<SecurityPrincipal> principal = service.getActivePrincipal(6L);

        assertThat(principal).isPresent();
        assertThat(principal.orElseThrow().totpEnabled()).isTrue();
        assertThat(principal.orElseThrow().forceTotpSetup()).isFalse();
    }

    private UserAccountRepository repository(UserLoader loader) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> loader.load((Long) args[0]);
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleBindingService roleBindingService(List<RoleSetting> roles) {
        return new UserRoleBindingService(null, null, null) {
            @Override
            public List<RoleSetting> resolveRolesForUser(Long userId) {
                return roles;
            }

            @Override
            public List<org.springframework.security.core.GrantedAuthority> toGrantedAuthorities(java.util.Collection<RoleSetting> ignored) {
                return roles.stream()
                        .<org.springframework.security.core.GrantedAuthority>map(
                                role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode())
                        )
                        .toList();
            }
        };
    }

    @FunctionalInterface
    private interface UserLoader {
        Optional<UserAccount> load(Long userId);
    }

    private static final class StubRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values;
        private final ValueOperations<String, String> valueOperations;
        private final List<String> deletedKeys = new ArrayList<>();
        private List<String> indexMembers = List.of();
        private List<String> scanKeys = List.of();
        private boolean hasIndexKey = true;
        private boolean nullConnectionFactory;
        private boolean scanFallbackTriggered;
        private boolean connectionClosed;
        private boolean throwOnScan;
        private boolean throwOnClose;
        private String lastSetKey;
        private Duration lastSetTtl;

        private StubRedisTemplate(Map<String, String> values) {
            this.values = values;
            this.valueOperations = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class[]{ValueOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> this.values.get((String) args[0]);
                        case "set" -> {
                            this.lastSetKey = (String) args[0];
                            this.values.put((String) args[0], (String) args[1]);
                            this.lastSetTtl = args.length >= 3 && args[2] instanceof Duration duration ? duration : null;
                            yield null;
                        }
                        case "toString" -> "ValueOperationsStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        @Override
        public Boolean delete(String key) {
            values.remove(key);
            deletedKeys.add(key);
            return Boolean.TRUE;
        }

        @Override
        public Boolean expire(String key, java.time.Duration timeout) {
            return Boolean.TRUE;
        }

        @Override
        public SetOperations<String, String> opsForSet() {
            return (SetOperations<String, String>) Proxy.newProxyInstance(
                    SetOperations.class.getClassLoader(),
                    new Class[]{SetOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "add" -> 1L;
                        case "remove" -> 1L;
                        case "scan" -> new StringCursor(indexMembers);
                        case "toString" -> "SetOperationsStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        @Override
        public Boolean hasKey(String key) {
            if ("auth:user:snapshot:index".equals(key)) {
                return hasIndexKey;
            }
            return values.containsKey(key);
        }

        @Override
        public Long delete(java.util.Collection<String> keys) {
            deletedKeys.addAll(keys);
            keys.forEach(values::remove);
            return (long) keys.size();
        }

        @Override
        public RedisConnectionFactory getConnectionFactory() {
            scanFallbackTriggered = true;
            if (nullConnectionFactory) {
                return null;
            }
            return (RedisConnectionFactory) Proxy.newProxyInstance(
                    RedisConnectionFactory.class.getClassLoader(),
                    new Class[]{RedisConnectionFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getConnection" -> connection();
                        case "toString" -> "RedisConnectionFactoryStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private RedisConnection connection() {
            return (RedisConnection) Proxy.newProxyInstance(
                    RedisConnection.class.getClassLoader(),
                    new Class[]{RedisConnection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "scan" -> {
                            if (throwOnScan) {
                                throw new IllegalStateException("scan failed");
                            }
                            yield new ByteCursor(scanKeys);
                        }
                        case "close" -> {
                            connectionClosed = true;
                            if (throwOnClose) {
                                throw new IllegalStateException("close failed");
                            }
                            yield null;
                        }
                        case "isClosed" -> connectionClosed;
                        case "toString" -> "RedisConnectionStub";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static final class StringCursor implements Cursor<String> {

        private final Iterator<String> iterator;
        private long position;
        private boolean closed;

        private StringCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public String next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public CursorId getId() {
            return CursorId.initial();
        }

        @Override
        public long getCursorId() {
            return 0L;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }
    }

    private static final class ByteCursor implements Cursor<byte[]> {

        private final Iterator<String> iterator;
        private long position;
        private boolean closed;

        private ByteCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public byte[] next() {
            position++;
            return iterator.next().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public CursorId getId() {
            return CursorId.initial();
        }

        @Override
        public long getCursorId() {
            return 0L;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }
    }
}
