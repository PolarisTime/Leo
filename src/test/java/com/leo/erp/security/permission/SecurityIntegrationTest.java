package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityIntegrationTest {

    @BeforeEach
    void setUp() {
        DataScopeContext.clear();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ── DataScope enforcement (cross-department boundary) ──

    @Test
    void shouldRejectCrossUserAccessAtDataScopeSelf() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "payment", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "payment", "read"))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(1L));

        ResourceRecordAccessGuard guard = createGuard(permissionService);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "userA", List.of());

        Payment payment = new Payment();
        payment.setCreatedBy(2L);

        DataScopeContext.set(1L, "payment", ResourcePermissionCatalog.SCOPE_SELF, Set.of(1L));

        assertThatThrownBy(() -> guard.assertCanAccess(principal, "payment", "read", payment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldAllowDepartmentLevelAccessForSameDepartmentUser() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "payment", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "payment", "read"))
                .thenReturn(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT))
                .thenReturn(Set.of(1L, 2L));

        ResourceRecordAccessGuard guard = createGuard(permissionService);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "userA", List.of());

        Payment payment = new Payment();
        payment.setCreatedBy(2L);

        DataScopeContext.set(1L, "payment", ResourcePermissionCatalog.SCOPE_DEPARTMENT, Set.of(1L, 2L));

        guard.assertCanAccess(principal, "payment", "read", payment);
    }

    @Test
    void shouldAllowAllDataAccessAtDataScopeAll() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "payment", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "payment", "read"))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_ALL))
                .thenReturn(Collections.emptySet());

        ResourceRecordAccessGuard guard = createGuard(permissionService);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());

        Payment payment = new Payment();
        payment.setCreatedBy(999L);

        DataScopeContext.set(1L, "payment", ResourcePermissionCatalog.SCOPE_ALL, Collections.emptySet());

        guard.assertCanAccess(principal, "payment", "read", payment);
    }

    // ── Module permission enforcement ──

    @Test
    void shouldRejectUserWithoutModulePermission() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "payment", "create")).thenReturn(false);
        ModulePermissionGuard guard = new ModulePermissionGuard(permissionService);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "userA", List.of());

        assertThatThrownBy(() -> guard.requireResourcePermission(principal, "payment", "create"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
    }

    @Test
    void shouldAllowUserWithModulePermission() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "payment", "read")).thenReturn(true);
        ModulePermissionGuard guard = new ModulePermissionGuard(permissionService);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "userA", List.of());

        ModulePermissionGuard.PermissionCheck result = guard.requireResourcePermission(principal, "payment", "read");
        assertThat(result.moduleKey()).isEqualTo("payment");
        assertThat(result.resource()).isEqualTo("payment");
    }

    private ResourceRecordAccessGuard createGuard(PermissionService permissionService) {
        return new ResourceRecordAccessGuard(
                new ModulePermissionGuard(permissionService), permissionService);
    }
}
