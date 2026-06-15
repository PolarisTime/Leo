package com.leo.erp.security.permission;

import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
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
                    case "isPipelined" -> false;
                    case "isQueueing" -> false;
                    case "closePipeline" -> null;
                    case "openPipeline" -> null;
                    case "exists" -> false;
                    case "expire" -> true;
                    case "del" -> 1L;
                    case "type" -> null;
                    case "scriptLoad" -> "return 0";
                    case "evalSha" -> null;
                    case "eval" -> null;
                    case "zAdd" -> true;
                    case "zRem" -> 1L;
                    case "hSet" -> true;
                    case "hDel" -> 1L;
                    case "sAdd" -> 1L;
                    case "sRem" -> 1L;
                    case "sMembers" -> java.util.Set.of();
                    case "multi" -> null;
                    case "exec" -> List.of();
                    case "watch" -> null;
                    case "unwatch" -> null;
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
        PermissionService service = permissionService(null, null, null, redisTemplate, null, new RedisTuningProperties());

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

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 9L))),
                rolePermissionRepository(action),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
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

        PermissionService service = permissionService(
                userRoleRepository(List.of()),
                rolePermissionRepository(action),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
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

        PermissionService service = permissionService(
                null,
                rolePermissionRepository(view, edit),
                null,
                noOpRedisTemplate(),
                null,
                new RedisTuningProperties()
        );

        assertThat(service.getPermissionSummaryForRoles(List.of(role))).isEqualTo("商品资料-查看、商品资料-编辑");
    }

    @Test
    void shouldResolveDataScopeOnlyFromRolesGrantingRequestedAction() {
        RoleSetting readRole = role(10L, "READ_ALL", "全部数据");
        RoleSetting updateRole = role(20L, "UPDATE_SELF", "本人");
        RolePermission read = permission(10L, "purchase-order", "read");
        RolePermission update = permission(20L, "purchase-order", "update");

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L), binding(1L, 20L))),
                rolePermissionRepository(read, update),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(readRole, updateRole),
                new RedisTuningProperties()
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

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L), binding(1L, 20L))),
                countingRolePermissionRepository(permissionQueries, read, update),
                null,
                inMemoryRedisTemplate(),
                roleSettingRepository(readRole, updateRole),
                new RedisTuningProperties()
        );

        assertThat(service.can(1L, "purchase-order", "read")).isTrue();
        assertThat(service.getUserDataScope(1L, "purchase-order", "read")).isEqualTo(ResourcePermissionCatalog.SCOPE_ALL);
        assertThat(service.getUserDataScope(1L, "purchase-order", "update")).isEqualTo(ResourcePermissionCatalog.SCOPE_SELF);
        assertThat(permissionQueries.get()).isEqualTo(1);
    }

    @Test
    void shouldReturnSortedPermissionListFromGetUserPermissions() {
        RoleSetting role = role(10L, "READ_ALL", "全部数据");
        RolePermission read = permission(10L, "material", "read");
        RolePermission update = permission(10L, "material", "update");

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L))),
                rolePermissionRepository(read, update),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
        );

        var result = service.getUserPermissions(1L);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().resource()).isEqualTo("material");
        assertThat(result.getFirst().actions()).containsExactlyInAnyOrder("read", "update");
    }

    @Test
    void shouldReturnDataScopesPerResource() {
        RoleSetting readRole = role(10L, "READ_ALL", "全部数据");
        RoleSetting updateRole = role(20L, "UPDATE_SELF", "本人");
        RolePermission readMat = permission(10L, "material", "read");
        RolePermission updateMat = permission(20L, "material", "update");

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L), binding(1L, 20L))),
                rolePermissionRepository(readMat, updateMat),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(readRole, updateRole),
                new RedisTuningProperties()
        );

        Map<String, String> scopes = service.getUserDataScopes(1L);
        assertThat(scopes).containsEntry("material", ResourcePermissionCatalog.SCOPE_ALL);
    }

    @Test
    void shouldReturnSelfPermissionWhenNoDataScopeForResource() {
        PermissionService service = permissionService(
                userRoleRepository(List.of()),
                rolePermissionRepository(),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(),
                new RedisTuningProperties()
        );

        assertThat(service.getUserDataScope(1L, "unknown")).isEqualTo(ResourcePermissionCatalog.SCOPE_SELF);
    }

    @Test
    void shouldDelegateGetDataScopeOwnerUserIds() {
        PermissionService service = permissionService(
                userRoleRepository(List.of()),
                rolePermissionRepository(),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(),
                new RedisTuningProperties()
        );

        assertThat(service.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF)).containsExactly(1L);
    }

    @Test
    void shouldDelegateGetVisibleMenuCodes() {
        RoleSetting role = role(10L, "ADMIN", "全部数据");
        RolePermission perm = permission(10L, "material", "read");

        com.leo.erp.system.menu.domain.entity.Menu m = new com.leo.erp.system.menu.domain.entity.Menu();
        m.setMenuCode("material");
        m.setMenuName("商品管理");
        m.setStatus("正常");
        m.setSortOrder(1);

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L))),
                rolePermissionRepository(perm),
                menuRepository(m),
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
        );

        assertThat(service.getVisibleMenuCodes(1L)).contains("material");
    }

    @Test
    void shouldDelegateGetActiveMenus() {
        com.leo.erp.system.menu.domain.entity.Menu m = menu("order", "订单管理");

        PermissionService service = permissionService(
                null,
                null,
                menuRepository(m),
                noOpRedisTemplate(),
                null,
                new RedisTuningProperties()
        );

        assertThat(service.getActiveMenus()).hasSize(1);
    }

    @Test
    void shouldDelegateEvictCache() {
        Map<String, Map<Object, Object>> hashes = new java.util.LinkedHashMap<>();
        List<String> deletedKeys = new ArrayList<>();
        StringRedisTemplate redis = evictableRedisTemplate(hashes, deletedKeys);

        PermissionService service = permissionService(
                null, null, null, redis, null, new RedisTuningProperties()
        );

        service.evictCache(1L);
        assertThat(deletedKeys).isNotEmpty();
    }

    @Test
    void shouldDelegateEvictUserCaches() {
        Map<String, Map<Object, Object>> hashes = new java.util.LinkedHashMap<>();
        List<String> deletedKeys = new ArrayList<>();
        StringRedisTemplate redis = evictableRedisTemplate(hashes, deletedKeys);

        PermissionService service = permissionService(
                null, null, null, redis, null, new RedisTuningProperties()
        );

        service.evictUserCaches(List.of(1L, 2L));
        assertThat(deletedKeys).hasSize(4);
    }

    @Test
    void shouldDelegateEvictMetadataCache() {
        Map<String, Map<Object, Object>> hashes = new java.util.LinkedHashMap<>();
        List<String> deletedKeys = new ArrayList<>();
        StringRedisTemplate redis = evictableRedisTemplate(hashes, deletedKeys);

        com.leo.erp.common.support.RedisJsonCacheSupport cacheSupport =
                new com.leo.erp.common.support.RedisJsonCacheSupport(redis, new com.fasterxml.jackson.databind.ObjectMapper(), new RedisTuningProperties());

        PermissionService service = permissionService(
                null, null, null, redis, null,
                java.util.Optional.of(cacheSupport),
                new RedisTuningProperties()
        );

        service.evictMetadataCache();
        assertThat(deletedKeys).isNotEmpty();
    }

    @Test
    void shouldResolveBlankResourceToSelfScope() {
        RoleSetting role = role(10L, "READ_ALL", "全部数据");
        RolePermission perm = permission(10L, " ", "read");

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L))),
                rolePermissionRepository(perm),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
        );

        Map<String, String> scopes = service.getUserDataScopes(1L);
        assertThat(scopes).doesNotContainKey("");
    }

    @Test
    void shouldUseConstructorWithRedisJsonCacheSupport() {
        RoleSetting role = role(10L, "ADMIN", "全部数据");
        RolePermission perm = permission(10L, "material", "read");

        PermissionService service = permissionService(
                userRoleRepository(List.of(binding(1L, 10L))),
                rolePermissionRepository(perm),
                null,
                noOpRedisTemplate(),
                roleSettingRepository(role),
                new RedisTuningProperties()
        );

        assertThat(service.can(1L, "material", "read")).isTrue();
    }

    private PermissionService permissionService(UserRoleRepository userRoleRepository,
                                                RolePermissionRepository rolePermissionRepository,
                                                MenuRepository menuRepository,
                                                StringRedisTemplate redisTemplate,
                                                RoleSettingRepository roleSettingRepository,
                                                RedisTuningProperties redisTuningProperties) {
        return permissionService(
                userRoleRepository,
                rolePermissionRepository,
                menuRepository,
                redisTemplate,
                roleSettingRepository,
                java.util.Optional.empty(),
                redisTuningProperties
        );
    }

    private PermissionService permissionService(UserRoleRepository userRoleRepository,
                                                RolePermissionRepository rolePermissionRepository,
                                                MenuRepository menuRepository,
                                                StringRedisTemplate redisTemplate,
                                                RoleSettingRepository roleSettingRepository,
                                                java.util.Optional<com.leo.erp.common.support.RedisJsonCacheSupport> redisJsonCacheSupport,
                                                RedisTuningProperties redisTuningProperties) {
        PermissionCache cache = new PermissionCache(redisTemplate, redisJsonCacheSupport, redisTuningProperties);
        return new PermissionService(
                new PermissionResolver(userRoleRepository, rolePermissionRepository, roleSettingRepository, cache),
                cache,
                new MenuVisibilityService(menuRepository, redisJsonCacheSupport),
                new DepartmentScopeResolver(java.util.Optional.empty(), java.util.Optional.empty())
        );
    }

    private static final class RecordingStringRedisTemplate extends StringRedisTemplate {

        private final RedisConnectionFactory connectionFactory;
        private final List<String> deletedKeys = new ArrayList<>();

        private RecordingStringRedisTemplate(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            try { afterPropertiesSet(); } catch (Exception ignored) {}
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
            { try { afterPropertiesSet(); } catch (Exception ignored) {} }
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
            public SetOperations<String, String> opsForSet() {
                return (SetOperations<String, String>) Proxy.newProxyInstance(
                        SetOperations.class.getClassLoader(),
                        new Class[]{SetOperations.class},
                        (p, m, a) -> switch (m.getName()) {
                            case "add" -> 1L;
                            case "toString" -> "SetOperationsStub";
                            case "hashCode" -> System.identityHashCode(p);
                            case "equals" -> p == a[0];
                            default -> throw new UnsupportedOperationException(m.getName());
                        });
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
                    case "findByIdAndDeletedFlagFalse" -> java.util.Optional.ofNullable(roles.length > 0 ? roles[0] : null);
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
            { try { afterPropertiesSet(); } catch (Exception ignored) {} }
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
            public SetOperations<String, String> opsForSet() {
                return (SetOperations<String, String>) Proxy.newProxyInstance(
                        SetOperations.class.getClassLoader(),
                        new Class[]{SetOperations.class},
                        (p, m, a) -> switch (m.getName()) {
                            case "add" -> 1L;
                            case "toString" -> "SetOperationsStub";
                            case "hashCode" -> System.identityHashCode(p);
                            case "equals" -> p == a[0];
                            default -> throw new UnsupportedOperationException(m.getName());
                        });
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

    @SuppressWarnings("unchecked")
    private StringRedisTemplate evictableRedisTemplate(Map<String, Map<Object, Object>> hashes, List<String> deletedKeys) {
        return new StringRedisTemplate() {
            { try { afterPropertiesSet(); } catch (Exception ignored) {} }
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
                                case "delete" -> {
                                    hashes.remove((String) args[0]);
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
            public SetOperations<String, String> opsForSet() {
                return (SetOperations<String, String>) Proxy.newProxyInstance(
                        SetOperations.class.getClassLoader(),
                        new Class[]{SetOperations.class},
                        (p, m, a) -> switch (m.getName()) {
                            case "add" -> 1L;
                            case "remove" -> 1L;
                            case "toString" -> "SetOperationsStub";
                            case "hashCode" -> System.identityHashCode(p);
                            case "equals" -> p == a[0];
                            default -> throw new UnsupportedOperationException(m.getName());
                        });
            }

            @Override
            public Boolean hasKey(String key) {
                return hashes.containsKey(key);
            }

            @Override
            public Boolean delete(String key) {
                deletedKeys.add(key);
                hashes.remove(key);
                return Boolean.TRUE;
            }

            @Override
            public Long delete(Collection<String> keys) {
                deletedKeys.addAll(keys);
                keys.forEach(hashes::remove);
                return (long) keys.size();
            }

            @Override
            public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
                return Boolean.TRUE;
            }
        };
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
