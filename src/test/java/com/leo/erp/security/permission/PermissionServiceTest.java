package com.leo.erp.security.permission;

import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    @Test
    void shouldScanAndDeleteAllPermissionCacheKeys() {
        AtomicBoolean connectionClosed = new AtomicBoolean(false);
        AtomicInteger scanCount = new AtomicInteger();
        RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class[]{RedisConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "scan" -> scanCount.getAndIncrement() == 0
                            ? new ByteArrayCursor(List.of(
                                    "leo:resource-perm:100".getBytes(StandardCharsets.UTF_8),
                                    "leo:resource-perm:200".getBytes(StandardCharsets.UTF_8)
                            ))
                            : new ByteArrayCursor(List.of(
                                    "leo:resource-scope:100".getBytes(StandardCharsets.UTF_8)
                            ));
                    case "close" -> {
                        connectionClosed.set(true);
                        yield null;
                    }
                    case "keys" -> throw new AssertionError("KEYS should not be used for cache eviction");
                    case "isClosed" -> connectionClosed.get();
                    case "toString" -> "RedisConnectionStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RedisConnectionFactory connectionFactory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class[]{RedisConnectionFactory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "toString" -> "RedisConnectionFactoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RecordingStringRedisTemplate redisTemplate = new RecordingStringRedisTemplate(connectionFactory);
        PermissionService service = new PermissionService(null, null, null, redisTemplate, null, null);

        service.evictAllCache();

        assertThat(redisTemplate.deletedKeys).containsExactly(
                "leo:resource-perm:100",
                "leo:resource-perm:200",
                "leo:resource-scope:100"
        );
        assertThat(connectionClosed.get()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseUserRoleBindingsAsOnlyRoleSource() {
        RoleSetting role = new RoleSetting();
        role.setId(9L);
        role.setRoleCode("ADMIN");
        role.setRoleName("系统管理员");
        role.setStatus("正常");
        RolePermission action = new RolePermission();
        action.setRoleId(9L);
        action.setResourceCode("material");
        action.setActionCode("read");

        PermissionService service = new PermissionService(
                userRoleRepository(List.of(binding(1L, 9L))),
                rolePermissionRepository(action),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role)
        );

        assertThat(service.can(1L, "material", "read")).isTrue();
    }

    @Test
    void shouldNotGrantPermissionWhenUserRoleBindingIsMissing() {
        RoleSetting role = new RoleSetting();
        role.setId(9L);
        role.setRoleCode("ADMIN");
        role.setRoleName("系统管理员");
        role.setStatus("正常");
        RolePermission action = new RolePermission();
        action.setRoleId(9L);
        action.setResourceCode("material");
        action.setActionCode("read");

        PermissionService service = new PermissionService(
                userRoleRepository(List.of()),
                rolePermissionRepository(action),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role)
        );

        assertThat(service.can(1L, "material", "read")).isFalse();
    }

    @Test
    void shouldBuildPermissionSummaryFromRolePermissions() {
        RoleSetting role = new RoleSetting();
        role.setId(9L);
        role.setStatus("正常");

        RolePermission view = new RolePermission();
        view.setRoleId(9L);
        view.setResourceCode("material");
        view.setActionCode("read");
        RolePermission edit = new RolePermission();
        edit.setRoleId(9L);
        edit.setResourceCode("material");
        edit.setActionCode("update");

        PermissionService service = new PermissionService(
                null,
                rolePermissionRepository(view, edit),
                null,
                noOpRedisTemplate(),
                null
        );

        assertThat(service.getPermissionSummaryForRoles(List.of(role))).isEqualTo("商品资料-查看、商品资料-编辑");
    }

    @Test
    void shouldResolveDataScopeOnlyFromRolesGrantingRequestedAction() {
        RoleSetting readRole = role(10L, "READ_ALL", "全部数据");
        RoleSetting updateRole = role(20L, "UPDATE_SELF", "本人");
        RolePermission read = permission(10L, "purchase-order", "read");
        RolePermission update = permission(20L, "purchase-order", "update");

        PermissionService service = new PermissionService(
                userRoleRepository(List.of(binding(1L, 10L), binding(1L, 20L))),
                rolePermissionRepository(read, update),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(readRole, updateRole)
        );

        assertThat(service.getUserDataScope(1L, "purchase-order", "read")).isEqualTo(ResourcePermissionCatalog.SCOPE_ALL);
        assertThat(service.getUserDataScope(1L, "purchase-order", "update")).isEqualTo(ResourcePermissionCatalog.SCOPE_SELF);
    }

    @Test
    void shouldCacheActionDataScopesWithPermissionMap() {
        RoleSetting readRole = role(10L, "READ_ALL", "全部数据");
        RoleSetting updateRole = role(20L, "UPDATE_SELF", "本人");
        RolePermission read = permission(10L, "purchase-order", "read");
        RolePermission update = permission(20L, "purchase-order", "update");
        AtomicInteger permissionQueries = new AtomicInteger();

        PermissionService service = new PermissionService(
                userRoleRepository(List.of(binding(1L, 10L), binding(1L, 20L))),
                countingRolePermissionRepository(permissionQueries, read, update),
                null,
                inMemoryRedisTemplate(),
                roleSettingRepository(readRole, updateRole)
        );

        assertThat(service.can(1L, "purchase-order", "read")).isTrue();
        assertThat(service.getUserDataScope(1L, "purchase-order", "read")).isEqualTo(ResourcePermissionCatalog.SCOPE_ALL);
        assertThat(service.getUserDataScope(1L, "purchase-order", "update")).isEqualTo(ResourcePermissionCatalog.SCOPE_SELF);
        assertThat(permissionQueries.get()).isEqualTo(1);
    }

    private static final class RecordingStringRedisTemplate extends StringRedisTemplate {

        private final RedisConnectionFactory connectionFactory;
        private final List<String> deletedKeys = new ArrayList<>();

        private RecordingStringRedisTemplate(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public RedisConnectionFactory getConnectionFactory() {
            return connectionFactory;
        }

        @Override
        public Long delete(Collection<String> keys) {
            deletedKeys.addAll(keys);
            return (long) keys.size();
        }
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate noOpRedisTemplate() {
        return new StringRedisTemplate() {
            private final HashOperations<String, Object, Object> hashOperations =
                    (HashOperations<String, Object, Object>) Proxy.newProxyInstance(
                            HashOperations.class.getClassLoader(),
                            new Class[]{HashOperations.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "entries" -> Map.of();
                                case "putAll" -> null;
                                case "toString" -> "HashOperationsStub";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> throw new UnsupportedOperationException(method.getName());
                            }
                    );

            @Override
            public HashOperations<String, Object, Object> opsForHash() {
                return hashOperations;
            }

            @Override
            public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
                return Boolean.TRUE;
            }
        };
    }

    private UserRoleRepository userRoleRepository(List<com.leo.erp.auth.domain.entity.UserRole> bindings) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalse" -> bindings;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepository(RoleSetting... roles) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdInAndDeletedFlagFalse" -> Arrays.asList(roles);
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSetting role(Long id, String code, String dataScope) {
        RoleSetting role = new RoleSetting();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus("正常");
        role.setDataScope(dataScope);
        return role;
    }

    private RolePermission permission(Long roleId, String resource, String actionCode) {
        RolePermission permission = new RolePermission();
        permission.setRoleId(roleId);
        permission.setResourceCode(resource);
        permission.setActionCode(actionCode);
        return permission;
    }

    private com.leo.erp.auth.domain.entity.UserRole binding(Long userId, Long roleId) {
        com.leo.erp.auth.domain.entity.UserRole binding = new com.leo.erp.auth.domain.entity.UserRole();
        binding.setId(roleId * 10);
        binding.setUserId(userId);
        binding.setRoleId(roleId);
        return binding;
    }

    private RolePermissionRepository rolePermissionRepository(RolePermission action) {
        return rolePermissionRepository(new RolePermission[]{action});
    }

    private RolePermissionRepository rolePermissionRepository(RolePermission... actions) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdInAndDeletedFlagFalse" -> Arrays.asList(actions);
                    case "toString" -> "RolePermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RolePermissionRepository countingRolePermissionRepository(AtomicInteger queries, RolePermission... actions) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdInAndDeletedFlagFalse" -> {
                        queries.incrementAndGet();
                        yield Arrays.asList(actions);
                    }
                    case "toString" -> "CountingRolePermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate inMemoryRedisTemplate() {
        Map<String, Map<Object, Object>> hashes = new java.util.LinkedHashMap<>();
        return new StringRedisTemplate() {
            private final HashOperations<String, Object, Object> hashOperations =
                    (HashOperations<String, Object, Object>) Proxy.newProxyInstance(
                            HashOperations.class.getClassLoader(),
                            new Class[]{HashOperations.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "entries" -> hashes.getOrDefault((String) args[0], Map.of());
                                case "putAll" -> {
                                    hashes.computeIfAbsent((String) args[0], key -> new java.util.LinkedHashMap<>())
                                            .putAll((Map<?, ?>) args[1]);
                                    yield null;
                                }
                                case "toString" -> "HashOperationsStub";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> throw new UnsupportedOperationException(method.getName());
                            }
                    );

            @Override
            public HashOperations<String, Object, Object> opsForHash() {
                return hashOperations;
            }

            @Override
            public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
                return Boolean.TRUE;
            }
        };
    }

    private com.leo.erp.system.menu.repository.MenuRepository menuRepository(com.leo.erp.system.menu.domain.entity.Menu... menus) {
        return (com.leo.erp.system.menu.repository.MenuRepository) Proxy.newProxyInstance(
                com.leo.erp.system.menu.repository.MenuRepository.class.getClassLoader(),
                new Class[]{com.leo.erp.system.menu.repository.MenuRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrder" -> List.of(menus);
                    case "toString" -> "MenuRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private com.leo.erp.system.menu.repository.MenuActionRepository menuActionRepository(com.leo.erp.system.menu.domain.entity.MenuAction... actions) {
        return (com.leo.erp.system.menu.repository.MenuActionRepository) Proxy.newProxyInstance(
                com.leo.erp.system.menu.repository.MenuActionRepository.class.getClassLoader(),
                new Class[]{com.leo.erp.system.menu.repository.MenuActionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalse" -> List.of(actions);
                    case "toString" -> "MenuActionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private com.leo.erp.system.menu.domain.entity.Menu menu(String code, String name) {
        com.leo.erp.system.menu.domain.entity.Menu menu = new com.leo.erp.system.menu.domain.entity.Menu();
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setStatus("正常");
        menu.setSortOrder(1);
        return menu;
    }

    private com.leo.erp.system.menu.domain.entity.MenuAction menuAction(String menuCode, String actionCode, String actionName) {
        com.leo.erp.system.menu.domain.entity.MenuAction action = new com.leo.erp.system.menu.domain.entity.MenuAction();
        action.setMenuCode(menuCode);
        action.setActionCode(actionCode);
        action.setActionName(actionName);
        return action;
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
        public CursorId getId() {
            return CursorId.of(1L);
        }

        @Override
        public long getCursorId() {
            return 1L;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public byte[] next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}
