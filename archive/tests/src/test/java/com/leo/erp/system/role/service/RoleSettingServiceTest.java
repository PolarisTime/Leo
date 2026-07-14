package com.leo.erp.system.role.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.role.web.dto.RolePermissionItem;
import com.leo.erp.system.role.web.dto.RoleSettingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleSettingServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDuplicateRolePermissions() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(
                new RolePermissionItem("material", "read"),
                new RolePermissionItem("material", "read")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限列表存在重复项");
    }

    @Test
    void shouldRejectInvalidStatusFilterWhenPagingRoles() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.page(PageQuery.of(0, 20, null, null), null, "INVALID"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色状态不合法");
    }

    @Test
    void shouldAddReadPermissionWhenSavingMutatingPermission() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(saved),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "update")));

        assertThat(saved.get())
                .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .containsExactly("material:update", "material:read");
    }

    @Test
    void shouldRejectNullPermissionList() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.saveRolePermissions(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限列表不能为空");
    }

    @Test
    void shouldRejectInvalidResourcePermission() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(
                new RolePermissionItem("invalid-resource", "read")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在无效的资源权限配置");
    }

    @Test
    void shouldSaveValidPermissions() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(saved),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

        assertThat(saved.get())
                .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .containsExactly("material:read");
    }

    @Test
    void shouldReturnRolePermissions() {
        RolePermissionRepository rolePermissionRepository = (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdAndDeletedFlagFalse" -> {
                        RolePermission permission = new RolePermission();
                        permission.setResourceCode("material");
                        permission.setActionCode("read");
                        yield List.of(permission);
                    }
                    case "toString" -> "RolePermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository,
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var result = service.getRolePermissions(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).resource()).isEqualTo("material");
        assertThat(result.get(0).action()).isEqualTo("read");
    }

    @Test
    void shouldPageRoles() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        role.setRoleName("管理员");
                        role.setRoleType("系统角色");
                        role.setDataScope("全部数据");
                        role.setStatus("正常");
                        yield new PageImpl<>(List.of(role));
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var result = service.page(PageQuery.of(0, 20, null, null), null, null);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldValidateRoleCodeUniquenessOnCreate() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByRoleCodeAndDeletedFlagFalse" -> "ADMIN".equals(args[0]);
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                " admin ", "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void shouldRejectNonAdminCreatingAdminRoleWithDifferentCase() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                " admin ", "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("非系统管理员不能管理系统管理员角色");
    }

    @Test
    void shouldCreateRole() {
        AtomicReference<RoleSetting> savedRole = new AtomicReference<>();
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "save" -> {
                        savedRole.set((RoleSetting) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var result = service.create(new RoleSettingRequest(
                " purchaser ", "采购员", "业务角色", "全部数据", null, null, "正常", null
        ));
        assertThat(result).isNotNull();
        assertThat(savedRole.get()).isNotNull();
        assertThat(savedRole.get().getRoleCode()).isEqualTo("PURCHASER");
    }

    @Test
    void shouldRejectInvalidRoleType() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "PURCHASER", "采购员", "无效角色类型", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色类型不合法");
    }

    @Test
    void shouldRejectInvalidDataScope() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "PURCHASER", "采购员", "业务角色", "无效数据范围", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据范围不合法");
    }

    @Test
    void shouldRejectInvalidStatus() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "PURCHASER", "采购员", "业务角色", "全部数据", null, null, "无效状态", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色状态不合法");
    }

    @Test
    void shouldRejectEmptyRoleCode() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "", "采购员", "业务角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void shouldRejectEmptyRoleName() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "PURCHASER", "", "业务角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色名称不能为空");
    }

    @Test
    void shouldRejectLongRoleCode() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        String longCode = "A".repeat(65);
        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                longCode, "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码长度不能超过64");
    }

    @Test
    void shouldListPermissionOptions() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var result = service.listPermissionOptions();
        assertThat(result).isNotNull();
    }

    @Test
    void shouldListUnknownPermissionGroupLastAndAllowEmptyPathPrefix() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );
        ResourcePermissionCatalog.Entry known = new ResourcePermissionCatalog.Entry(
                "material-test",
                "商品测试",
                "主数据",
                List.of("/material-test"),
                List.of(new ResourcePermissionCatalog.ActionOption("read", "查看")),
                true
        );
        ResourcePermissionCatalog.Entry unknown = new ResourcePermissionCatalog.Entry(
                "custom-test",
                "自定义测试",
                "未知分组",
                List.of(),
                List.of(new ResourcePermissionCatalog.ActionOption("read", "查看")),
                false
        );

        try (var catalog = mockStatic(ResourcePermissionCatalog.class)) {
            catalog.when(ResourcePermissionCatalog::entries).thenReturn(List.of(unknown, known));

            var result = service.listPermissionOptions();

            assertThat(result).extracting(item -> item.menuName()).containsExactly("主数据", "未知分组");
            assertThat(result.getLast().children().getFirst().routePath()).isNull();
        }
    }


    private RoleSettingRepository roleRepository() {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId((Long) args[0]);
                        role.setRoleCode("PURCHASER");
                        role.setRoleName("采购员");
                        role.setRoleType("业务角色");
                        role.setDataScope("全部数据");
                        role.setStatus("正常");
                        yield Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleRepositoryWithRole(String roleCode) {
        return roleRepositoryWithRole(roleCode, "全部数据");
    }

    private RoleSettingRepository roleRepositoryWithRole(String roleCode, String dataScope) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode(roleCode);
                        role.setRoleName("管理员");
                        role.setRoleType("系统角色");
                        role.setDataScope(dataScope);
                        role.setStatus("正常");
                        yield Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "RoleSettingRepositoryWithRoleStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RolePermissionRepository rolePermissionRepository() {
        return rolePermissionRepository(new AtomicReference<>(List.of()));
    }

    private RolePermissionRepository rolePermissionRepository(List<RolePermission> permissions) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdAndDeletedFlagFalse", "findByRoleIdInAndDeletedFlagFalse" -> permissions;
                    case "deleteActiveByRoleId" -> 0;
                    case "flush" -> null;
                    case "saveAll" -> args[0];
                    case "toString" -> "RolePermissionRepositoryListStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private RolePermissionRepository rolePermissionRepository(AtomicReference<List<RolePermission>> saved) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdAndDeletedFlagFalse" -> List.of();
                    case "findByRoleIdInAndDeletedFlagFalse" -> List.of();
                    case "deleteActiveByRoleId" -> 0;
                    case "flush" -> null;
                    case "saveAll" -> {
                        saved.set((List<RolePermission>) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "RolePermissionRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T repository(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdInAndDeletedFlagFalse" -> List.of();
                    case "toString" -> type.getSimpleName() + "Stub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepository(List<UserRole> userRoles) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdInAndDeletedFlagFalse" -> userRoles;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PermissionService permissionService() {
        return permissionService(Map.of());
    }

    private PermissionService permissionService(Map<String, Set<String>> permissionMap) {
        return permissionService(permissionMap, Map.of());
    }

    private PermissionService permissionService(Map<String, Set<String>> permissionMap, Map<String, String> dataScopeMap) {
        return new PermissionService() {
            @Override
            public Map<String, Set<String>> getUserPermissionMap(Long userId) {
                return permissionMap;
            }

            @Override
            public String getUserDataScope(Long userId, String resourceCode, String actionCode) {
                return dataScopeMap.getOrDefault(resourceCode + ":" + actionCode, "all");
            }

            @Override
            public void evictAllCache() {
            }
        };
    }

    private RolePermission rolePermission(Long roleId, String resource, String action) {
        RolePermission permission = new RolePermission();
        permission.setRoleId(roleId);
        permission.setResourceCode(resource);
        permission.setActionCode(action);
        return permission;
    }

    private UserRole userRole(Long roleId, Long userId) {
        UserRole userRole = new UserRole();
        userRole.setRoleId(roleId);
        userRole.setUserId(userId);
        return userRole;
    }

    private void authenticate(Long userId, String username, List<SimpleGrantedAuthority> authorities) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(userId, username, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void shouldRejectDuplicateRoleCodeOnUpdate() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("OLD_CODE");
                        yield java.util.Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository, rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.update(1L, new RoleSettingRequest(
                "NEW_CODE", "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void shouldUpdateRole() {
        AtomicReference<RoleSetting> savedRole = new AtomicReference<>();
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("PURCHASER");
                        yield java.util.Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "save" -> {
                        savedRole.set((RoleSetting) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository, rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        var result = service.update(1L, new RoleSettingRequest(
                "PURCHASER", "采购员V2", "业务角色", "全部数据", null, null, "正常", "更新"
        ));
        assertThat(result).isNotNull();
        assertThat(savedRole.get()).isNotNull();
        assertThat(savedRole.get().getRoleName()).isEqualTo("采购员V2");
    }

    @Test
    void shouldRejectChangingAdminRoleCode() {
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("ADMIN"), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.update(1L, new RoleSettingRequest(
                "ADMIN2", "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统管理员角色编码不能修改");
    }

    @Test
    void shouldRejectDisablingAdminRole() {
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("ADMIN"), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.update(1L, new RoleSettingRequest(
                "ADMIN", "管理员", "系统角色", "全部数据", null, null, "禁用", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统管理员角色不能禁用");
    }

    @Test
    void shouldRejectChangingAdminRolePermissions() {
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("ADMIN"), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统管理员角色权限不能修改");
    }

    @Test
    void shouldRejectSavingPermissionOutsideCurrentPrincipalUpperBound() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER"), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read"))),
                mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "update"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身权限范围的权限");
    }

    @Test
    void shouldRejectSavingPermissionOutsideCurrentPrincipalDataScope() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "全部数据"), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read")), Map.of("material:read", "department")),
                mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldRejectUpdatingRoleToDataScopeOutsideCurrentPrincipal() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "本部门"),
                rolePermissionRepository(List.of(rolePermission(1L, "material", "read"))),
                repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read")), Map.of("material:read", "department")),
                mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.update(1L, new RoleSettingRequest(
                "PURCHASER", "采购员", "业务角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldAllowSavingPermissionWithinCurrentPrincipalUpperBound() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "本部门"), rolePermissionRepository(saved), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read", "update")), Map.of(
                        "material:read", "department",
                        "material:update", "department"
                )),
                mock(AuthenticatedUserCacheService.class));

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "update")));

        assertThat(saved.get())
                .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .containsExactly("material:update", "material:read");
    }

    @Test
    void shouldDeleteRole() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("PURCHASER");
                        yield java.util.Optional.of(role);
                    }
                    case "save" -> args[0];
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository, rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        service.delete(1L);
    }

    @Test
    void shouldRejectDeletingAdminRole() {
        RoleSettingRepository roleRepository = roleRepositoryWithRole("ADMIN");
        RoleSettingService service = new RoleSettingService(
                roleRepository, rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统管理员角色不能删除");
    }

    @Test
    void shouldRejectNullRoleCode() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(), rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                null, "管理员", "系统角色", "全部数据", null, null, "正常", null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void shouldReturnDetailResponse() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        role.setRoleName("管理员");
                        role.setRoleType("系统角色");
                        role.setDataScope("全部数据");
                        role.setStatus("正常");
                        yield java.util.Optional.of(role);
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        RoleSettingService service = new RoleSettingService(
                roleRepository, rolePermissionRepository(), repository(UserRoleRepository.class),
                new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

        var result = service.detail(1L);
        assertThat(result).isNotNull();
        assertThat(result.roleCode()).isEqualTo("ADMIN");
    }

    @Test
    void shouldSaveAccessControlPermissions() {
        for (String resource : List.of("user-account", "permission", "role")) {
            AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
            RoleSettingService service = new RoleSettingService(
                    roleRepository(), rolePermissionRepository(saved), repository(UserRoleRepository.class),
                    new SnowflakeIdGenerator(0L), permissionService(), mock(AuthenticatedUserCacheService.class));

            service.saveRolePermissions(1L, List.of(new RolePermissionItem(resource, "read")));

            assertThat(saved.get())
                    .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                    .contains(resource + ":read", "access-control:read");
        }
    }

    @Test
    void shouldEvictRoleCachesAfterTransactionCommit() {
        PermissionService permissionService = mock(PermissionService.class);
        AuthenticatedUserCacheService authenticatedUserCacheService = mock(AuthenticatedUserCacheService.class);
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of(userRole(1L, 11L), userRole(1L, 11L), userRole(1L, 12L))),
                new SnowflakeIdGenerator(0L),
                permissionService,
                authenticatedUserCacheService
        );

        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

            verify(permissionService, never()).evictUserCaches(any());
            verify(authenticatedUserCacheService, never()).evict(anyLong());
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.afterCommit();

            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<Collection<Long>> userIds = org.mockito.ArgumentCaptor.forClass(Collection.class);
            verify(permissionService).evictUserCaches(userIds.capture());
            assertThat(userIds.getValue()).containsExactly(11L, 12L);
            verify(authenticatedUserCacheService).evict(11L);
            verify(authenticatedUserCacheService).evict(12L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void shouldEvictRoleCachesImmediatelyWithDashboardWhenNoTransactionSynchronization() {
        PermissionService permissionService = mock(PermissionService.class);
        AuthenticatedUserCacheService authenticatedUserCacheService = mock(AuthenticatedUserCacheService.class);
        DashboardSummaryService dashboardSummaryService = mock(DashboardSummaryService.class);
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of(userRole(1L, null), userRole(1L, 21L), userRole(1L, 21L), userRole(1L, 22L))),
                new SnowflakeIdGenerator(0L),
                permissionService,
                dashboardSummaryService,
                authenticatedUserCacheService
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<Long>> userIds = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(permissionService).evictUserCaches(userIds.capture());
        assertThat(userIds.getValue()).containsExactly(21L, 22L);
        verify(authenticatedUserCacheService).evict(21L);
        verify(authenticatedUserCacheService).evict(22L);
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void shouldSkipCacheEvictionWhenRoleHasNoAffectedUsers() {
        PermissionService permissionService = mock(PermissionService.class);
        AuthenticatedUserCacheService authenticatedUserCacheService = mock(AuthenticatedUserCacheService.class);
        DashboardSummaryService dashboardSummaryService = mock(DashboardSummaryService.class);
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService,
                dashboardSummaryService,
                authenticatedUserCacheService
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

        verify(permissionService, never()).evictUserCaches(any());
        verify(authenticatedUserCacheService, never()).evict(anyLong());
        verify(dashboardSummaryService, never()).evictAllCache();
    }

    @Test
    void shouldDeleteExistingPermissionsWithoutSavingWhenPermissionListIsEmpty() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>();
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of());

        assertThat(saved.get()).isNull();
    }

    @Test
    void shouldKeepReadOnlyPermissionWithoutAppendingAnotherReadPermission() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

        assertThat(saved.get())
                .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .containsExactly("material:read");
    }

    @Test
    void shouldPageRolesWithPermissionSummaryAndUserCount() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("PURCHASER");
                        role.setRoleName("采购员");
                        role.setRoleType("业务角色");
                        role.setDataScope("本部门");
                        role.setStatus("正常");
                        yield new PageImpl<>(List.of(role), Pageable.unpaged(), 1);
                    }
                    case "toString" -> "RoleSettingRepositoryWithStatsStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(List.of(
                        rolePermission(1L, "material", "update"),
                        rolePermission(1L, "material", "read")
                )),
                userRoleRepository(List.of(userRole(1L, 11L), userRole(1L, 12L))),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var page = service.page(PageQuery.of(0, 20, null, null), "采购", "正常");
        var response = page.getContent().getFirst();

        assertThat(response.permissionCodes()).containsExactly("material:read", "material:update");
        assertThat(response.permissionCount()).isEqualTo(2);
        assertThat(response.userCount()).isEqualTo(2);
        assertThat(response.permissionSummary()).isNotBlank();
    }

    @Test
    void shouldAllowAdminPrincipalToSavePermissionsWithoutUpperBoundMap() {
        authenticate(7L, "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "全部数据"),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "update")));

        assertThat(saved.get()).hasSize(2);
    }

    @Test
    void shouldAllowAnonymousCallerToSavePermissionsWithoutUpperBoundMap() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "全部数据"),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "update")));

        assertThat(saved.get()).hasSize(2);
    }

    @Test
    void shouldTreatAuthenticationWithNullAuthoritiesAsNonAdmin() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(7L, "operator", List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getAuthorities()).thenReturn(null);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(principal.username());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.create(new RoleSettingRequest(
                "ADMIN", "管理员", "系统角色", "全部数据", null, null, "正常", null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非系统管理员不能管理系统管理员角色");
    }

    @Test
    void shouldRejectMissingRoleWithNotFoundMessage() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "RoleSettingRepositoryMissingStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldUpdateAdminRoleWhenCodeAndStatusRemainNormal() {
        AtomicReference<RoleSetting> savedRole = new AtomicReference<>();
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        role.setRoleName("管理员");
                        role.setRoleType("系统角色");
                        role.setDataScope("全部数据");
                        role.setStatus("正常");
                        yield Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "save" -> {
                        savedRole.set((RoleSetting) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "RoleSettingRepositoryAdminUpdateStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var response = service.update(1L, new RoleSettingRequest(
                "ADMIN", "系统管理员", "系统角色", "全部数据", null, null, null, "保留"
        ));

        assertThat(response.roleCode()).isEqualTo("ADMIN");
        assertThat(savedRole.get().getStatus()).isEqualTo("正常");
        assertThat(savedRole.get().getRoleName()).isEqualTo("系统管理员");
    }

    @Test
    void shouldUpdateChangedRoleCodeWhenNextCodeIsUnique() {
        AtomicReference<RoleSetting> savedRole = new AtomicReference<>();
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> {
                        RoleSetting role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("OLD_CODE");
                        role.setRoleName("旧角色");
                        role.setRoleType("业务角色");
                        role.setDataScope("本部门");
                        role.setStatus("正常");
                        yield Optional.of(role);
                    }
                    case "existsByRoleCodeAndDeletedFlagFalse" -> false;
                    case "save" -> {
                        savedRole.set((RoleSetting) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "RoleSettingRepositoryChangedCodeStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.update(1L, new RoleSettingRequest(
                "NEW_CODE", "新角色", "业务角色", "本部门", null, null, "正常", null
        ));

        assertThat(savedRole.get().getRoleCode()).isEqualTo("NEW_CODE");
    }

    @Test
    void shouldPageEmptyRolesWithEmptyStatsSnapshot() {
        RoleSettingRepository roleRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> new PageImpl<RoleSetting>(List.of(), Pageable.unpaged(), 0);
                    case "toString" -> "RoleSettingRepositoryEmptyPageStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        RoleSettingService service = new RoleSettingService(
                roleRepository,
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThat(service.page(PageQuery.of(0, 20, null, null), null, null).getContent()).isEmpty();
    }

    @Test
    void shouldEvictImmediatelyWhenTransactionActiveButSynchronizationInactive() {
        PermissionService permissionService = mock(PermissionService.class);
        AuthenticatedUserCacheService authenticatedUserCacheService = mock(AuthenticatedUserCacheService.class);
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of(userRole(1L, 31L))),
                new SnowflakeIdGenerator(0L),
                permissionService,
                authenticatedUserCacheService
        );

        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);

            service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

            verify(permissionService).evictUserCaches(any());
            verify(authenticatedUserCacheService).evict(31L);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void shouldBuildStatsSnapshotWhenRolesArgumentIsNull() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        Object snapshot = ReflectionTestUtils.invokeMethod(service, "buildStatsSnapshot", (Object) null);

        assertThat(snapshot).isNotNull();
    }

    @Test
    void shouldBuildStatsSnapshotWhenAllRoleIdsAreNull() {
        RoleSetting role = new RoleSetting();
        role.setRoleCode("TEMP");
        role.setRoleName("临时角色");
        role.setRoleType("业务角色");
        role.setDataScope("本人");
        role.setStatus("正常");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        Object snapshot = ReflectionTestUtils.invokeMethod(service, "buildStatsSnapshot", List.of(role));

        assertThat(snapshot).isNotNull();
    }

    @Test
    void shouldConvertRoleThroughSingleArgumentToResponseOverride() {
        RolePermission permission = rolePermission(1L, "material", "read");
        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("PURCHASER");
        role.setRoleName("采购员");
        role.setRoleType("业务角色");
        role.setDataScope("本部门");
        role.setStatus("正常");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(List.of(permission)),
                userRoleRepository(List.of(userRole(1L, 11L))),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var response = (com.leo.erp.system.role.web.dto.RoleSettingResponse)
                ReflectionTestUtils.invokeMethod(service, "toResponse", role);

        assertThat(response).isNotNull();
        assertThat(response.permissionCodes()).containsExactly("material:read");
        assertThat(response.userCount()).isEqualTo(1);
    }

    @Test
    void shouldTreatNonSecurityPrincipalAuthenticationAsAnonymousWhenCheckingPermissionBounds() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("raw-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "全部数据"),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read")));

        assertThat(saved.get()).hasSize(1);
    }

    @Test
    void shouldTreatNullRoleAndBlankRoleCodeAsNonAdmin() {
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );
        RoleSetting blankRole = new RoleSetting();
        blankRole.setRoleCode(" ");

        Boolean nullRoleAdmin = ReflectionTestUtils.invokeMethod(service, "isAdminRole", (RoleSetting) null);
        Boolean blankRoleAdmin = ReflectionTestUtils.invokeMethod(service, "isAdminRole", blankRole);
        String normalizedBlank = ReflectionTestUtils.invokeMethod(service, "normalizeNullableRoleCode", " ");

        assertThat(nullRoleAdmin).isFalse();
        assertThat(blankRoleAdmin).isFalse();
        assertThat(normalizedBlank).isNull();
    }

    @Test
    void shouldValidateDataScopeWithSelfScopeWhenExistingPermissionsAreNull() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "全部数据", (List<RolePermission>) null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldValidateDataScopeWithSelfScopeWhenExistingPermissionsAreEmpty() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "全部数据", List.<RolePermission>of()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldValidateDataScopeWithSelfScopeWhenActionsByResourceIsNull() throws Exception {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );
        var method = RoleSettingService.class.getDeclaredMethod(
                "assertCurrentPrincipalCanGrantDataScope",
                String.class,
                Map.class
        );
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(service, "全部数据", (Map<String, Set<String>>) null))
                .hasCauseInstanceOf(BusinessException.class)
                .cause()
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldAllowGrantingSelfDataScopeWithValidExistingPermissions() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RolePermission permission = rolePermission(1L, "material", "read");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read")), Map.of("material:read", "self")),
                mock(AuthenticatedUserCacheService.class)
        );

        ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "本人", List.of(permission)
        );
    }

    @Test
    void shouldRejectBroaderDataScopeWithValidExistingPermissions() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RolePermission permission = rolePermission(1L, "material", "read");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of("material", Set.of("read")), Map.of("material:read", "self")),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "全部数据", List.of(permission)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldAllowAdminToGrantAnyDataScopeWithValidExistingPermissions() {
        authenticate(7L, "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        RolePermission permission = rolePermission(1L, "material", "read");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "全部数据", List.of(permission)
        );
    }

    @Test
    void shouldIgnoreInvalidExistingPermissionsWhenValidatingDataScope() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RolePermission invalidPermission = rolePermission(1L, "unknown-resource", "read");
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "assertCurrentPrincipalCanGrantDataScope", "全部数据", List.of(invalidPermission)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身数据范围的角色");
    }

    @Test
    void shouldResolveMenuCodeAliasWhenSavingPermissions() {
        AtomicReference<List<RolePermission>> saved = new AtomicReference<>(List.of());
        RoleSettingService service = new RoleSettingService(
                roleRepository(),
                rolePermissionRepository(saved),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        service.saveRolePermissions(1L, List.of(new RolePermissionItem("material-category", "view")));

        assertThat(saved.get())
                .extracting(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .containsExactly("material:read");
    }

    @Test
    void shouldRejectSavingPermissionsForNonAdminWithEmptyUpperBoundMap() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "本人"),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of(new RolePermissionItem("material", "read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能授予超出自身权限范围的权限");
    }

    @Test
    void shouldAllowNonAdminUpdatingSelfScopedRoleWithoutExistingPermissions() {
        authenticate(7L, "operator", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "本人"),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(Map.of()),
                mock(AuthenticatedUserCacheService.class)
        );

        var response = service.update(1L, new RoleSettingRequest(
                "PURCHASER", "采购员", "业务角色", "本人", null, null, "正常", null
        ));

        assertThat(response.dataScope()).isEqualTo("本人");
    }

    @Test
    void shouldAllowAdminPrincipalToUpdateAdminRole() {
        authenticate(7L, "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("ADMIN", "全部数据"),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        var response = service.update(1L, new RoleSettingRequest(
                "ADMIN", "系统管理员", "系统角色", "全部数据", null, null, "正常", null
        ));

        assertThat(response.roleCode()).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectSavingPermissionsWhenStoredRoleDataScopeIsInvalid() {
        RoleSettingService service = new RoleSettingService(
                roleRepositoryWithRole("PURCHASER", "自定义范围"),
                rolePermissionRepository(),
                userRoleRepository(List.of()),
                new SnowflakeIdGenerator(0L),
                permissionService(),
                mock(AuthenticatedUserCacheService.class)
        );

        assertThatThrownBy(() -> service.saveRolePermissions(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据范围不合法");
    }
}
