package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleBindingServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveRolesByRoleCodeOrRoleNameAndNormalizeStoredNames() {
        RoleSetting admin = role("ADMIN", "系统管理员", 11L);
        RoleSetting finance = role("FINANCE_MANAGER", "财务主管", 12L);
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(admin), List.of(finance)),
                new FixedIdGenerator(100L)
        );

        List<RoleSetting> roles = service.resolveRoles(List.of("ADMIN", "财务主管"));

        assertThat(roles).extracting(RoleSetting::getId).containsExactly(11L, 12L);
        assertThat(service.joinRoleNames(roles)).isEqualTo("系统管理员,财务主管");
    }

    @Test
    void shouldReplaceUserRolesWithResolvedBindings() {
        AtomicLong deletedUserId = new AtomicLong(-1L);
        AtomicReference<List<UserRole>> savedBindings = new AtomicReference<>(List.of());
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(deletedUserId, savedBindings),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L, 102L)
        );

        service.replaceUserRoles(9L, List.of(
                role("ADMIN", "系统管理员", 11L),
                role("FINANCE_MANAGER", "财务主管", 12L)
        ));

        assertThat(deletedUserId.get()).isEqualTo(9L);
        assertThat(savedBindings.get())
                .extracting(UserRole::getId, UserRole::getUserId, UserRole::getRoleId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, 9L, 11L),
                        org.assertj.core.groups.Tuple.tuple(102L, 9L, 12L)
                );
    }

    @Test
    void shouldRejectNonAdminBindingAdminRole() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L),
                rolePermissionRepository(List.of()),
                permissionService(Map.of())
        );

        assertThatThrownBy(() -> service.replaceUserRolesWithinCurrentPrincipalBounds(
                9L, List.of(role("admin", "系统管理员", 11L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非系统管理员不能授予系统管理员角色");
    }

    @Test
    void shouldRejectBindingRoleOutsideCurrentPrincipalPermissions() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSetting manager = role("MANAGER", "经理", 12L);
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L),
                rolePermissionRepository(List.of(permission(12L, "material", "update"))),
                permissionService(Map.of("material", Set.of("read")))
        );

        assertThatThrownBy(() -> service.replaceUserRolesWithinCurrentPrincipalBounds(9L, List.of(manager)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身权限范围的角色");
    }

    @Test
    void shouldRejectBindingRoleOutsideCurrentPrincipalDataScope() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSetting manager = role("MANAGER", "经理", 12L);
        manager.setDataScope("全部数据");
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L),
                rolePermissionRepository(List.of(permission(12L, "material", "read"))),
                permissionService(Map.of("material", Set.of("read")), Map.of("material:read", "department"))
        );

        assertThatThrownBy(() -> service.replaceUserRolesWithinCurrentPrincipalBounds(9L, List.of(manager)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldAllowBindingRoleWithinCurrentPrincipalPermissions() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        AtomicLong deletedUserId = new AtomicLong(-1L);
        AtomicReference<List<UserRole>> savedBindings = new AtomicReference<>(List.of());
        RoleSetting staff = role("STAFF", "员工", 12L);
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(deletedUserId, savedBindings),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L),
                rolePermissionRepository(List.of(permission(12L, "material", "read"))),
                permissionService(Map.of("material", Set.of("read")))
        );

        service.replaceUserRolesWithinCurrentPrincipalBounds(9L, List.of(staff));

        assertThat(deletedUserId.get()).isEqualTo(9L);
        assertThat(savedBindings.get()).extracting(UserRole::getRoleId).containsExactly(12L);
    }

    @Test
    void shouldRejectCurrentUserChangingOwnRoleSet() {
        authenticate(9L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L),
                rolePermissionRepository(List.of()),
                permissionService(Map.of())
        );

        assertThatThrownBy(() -> service.replaceUserRolesWithinCurrentPrincipalBounds(
                9L, List.of(role("STAFF", "员工", 12L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能修改自己的角色集合");
    }

    @Test
    void shouldRejectUnknownRoles() {
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(100L)
        );

        assertThatThrownBy(() -> service.resolveRoles(List.of("不存在的角色")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldResolveCurrentRoleNamesFromBindings() {
        AtomicReference<List<UserRole>> savedBindings = new AtomicReference<>(List.of());
        RoleSetting finance = role("FINANCE_MANAGER", "财务主管", 12L);
        finance.setStatus("正常");
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepositoryWithBindings(List.of(binding(12L, 9L)), savedBindings),
                roleSettingRepositoryWithIds(List.of(finance)),
                new FixedIdGenerator(100L)
        );

        List<RoleSetting> roles = service.resolveRolesForUser(9L);

        assertThat(roles).extracting(RoleSetting::getRoleName).containsExactly("财务主管");
    }

    @Test
    void shouldReturnEmptyWhenNoRoleBindingExists() {
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepositoryWithBindings(List.of(), null),
                roleSettingRepositoryWithIds(List.of()),
                new FixedIdGenerator(100L)
        );

        assertThat(service.resolveRolesForUser(9L)).isEmpty();
    }

    private UserRoleRepository userRoleRepository(AtomicLong deletedUserId, AtomicReference<List<UserRole>> savedBindings) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "deleteByUserIdAndDeletedFlagFalse" -> {
                        if (deletedUserId != null) {
                            deletedUserId.set((Long) args[0]);
                        }
                        yield null;
                    }
                    case "flush" -> null;
                    case "saveAll" -> {
                        if (savedBindings != null) {
                            savedBindings.set(new ArrayList<>((Collection<UserRole>) args[0]));
                        }
                        yield args[0];
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepositoryWithBindings(List<UserRole> bindings, AtomicReference<List<UserRole>> savedBindings) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalse" -> bindings;
                    case "saveAll" -> {
                        if (savedBindings != null) {
                            savedBindings.set(new ArrayList<>((Collection<UserRole>) args[0]));
                        }
                        yield args[0];
                    }
                    case "deleteByUserIdAndDeletedFlagFalse" -> null;
                    case "flush" -> null;
                    case "toString" -> "UserRoleRepositoryBindingStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepository(List<RoleSetting> codeMatches, List<RoleSetting> nameMatches) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeInAndDeletedFlagFalse" -> codeMatches;
                    case "findByRoleNameInAndDeletedFlagFalse" -> nameMatches;
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepositoryWithIds(List<RoleSetting> idMatches) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdInAndDeletedFlagFalse" -> idMatches;
                    case "findByRoleCodeInAndDeletedFlagFalse", "findByRoleNameInAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "RoleSettingRepositoryIdStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RolePermissionRepository rolePermissionRepository(List<RolePermission> permissions) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdInAndDeletedFlagFalse" -> permissions;
                    case "toString" -> "RolePermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PermissionService permissionService(Map<String, Set<String>> permissionMap) {
        return permissionService(permissionMap, Map.of());
    }

    private PermissionService permissionService(Map<String, Set<String>> permissionMap, Map<String, String> dataScopeMap) {
        return new PermissionService(null, null, null, null, null, null) {
            @Override
            public Map<String, Set<String>> getUserPermissionMap(Long userId) {
                return permissionMap;
            }

            @Override
            public String getUserDataScope(Long userId, String resourceCode, String actionCode) {
                return dataScopeMap.getOrDefault(resourceCode + ":" + actionCode, "all");
            }
        };
    }

    private RoleSetting role(String roleCode, String roleName, Long id) {
        RoleSetting role = new RoleSetting();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setDataScope("本部门");
        role.setStatus("正常");
        return role;
    }

    private UserRole binding(Long roleId, Long userId) {
        UserRole binding = new UserRole();
        binding.setId(roleId * 10);
        binding.setRoleId(roleId);
        binding.setUserId(userId);
        return binding;
    }

    private RolePermission permission(Long roleId, String resource, String action) {
        RolePermission permission = new RolePermission();
        permission.setRoleId(roleId);
        permission.setResourceCode(resource);
        permission.setActionCode(action);
        return permission;
    }

    private void authenticate(Long userId, String username, List<SimpleGrantedAuthority> authorities) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(userId, username, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long[] ids;
        private int index;

        private FixedIdGenerator(long... ids) {
            this.ids = ids;
        }

        @Override
        public synchronized long nextId() {
            return ids[index++];
        }
    }
}
