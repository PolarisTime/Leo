package com.leo.erp.system.role.service;

import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.role.web.dto.RolePermissionItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RoleSettingServiceTest {

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

    private RoleSettingRepository roleRepository() {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(new com.leo.erp.system.role.domain.entity.RoleSetting());
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RolePermissionRepository rolePermissionRepository() {
        return rolePermissionRepository(new AtomicReference<>(List.of()));
    }

    @SuppressWarnings("unchecked")
    private RolePermissionRepository rolePermissionRepository(AtomicReference<List<RolePermission>> saved) {
        return (RolePermissionRepository) Proxy.newProxyInstance(
                RolePermissionRepository.class.getClassLoader(),
                new Class[]{RolePermissionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleIdAndDeletedFlagFalse" -> List.of();
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
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PermissionService permissionService() {
        return new PermissionService(null, null, null, null, null, null) {
            @Override
            public void evictAllCache() {
            }
        };
    }
}
