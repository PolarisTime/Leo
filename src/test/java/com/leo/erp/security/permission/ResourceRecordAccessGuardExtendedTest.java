package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceRecordAccessGuardExtendedTest {

    private PermissionService permissionService;
    private ResourceRecordAccessGuard guard;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
        permissionService = mock(PermissionService.class);
        guard = new ResourceRecordAccessGuard(
                new ModulePermissionGuard(permissionService),
                permissionService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void shouldThrowWhenEntityIsNull() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());

        assertThatThrownBy(() -> guard.assertCanAccess(principal, "purchase-order", "read", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务记录不存在");
    }

    @Test
    void shouldAllowAccessWhenPermissionAndDataScopeMatch() {
        when(permissionService.can(1L, "purchase-order", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", "read")).thenReturn("all");
        when(permissionService.getDataScopeOwnerUserIds(1L, "all")).thenReturn(Set.of());

        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(99L);

        guard.assertCanAccess(principal, "purchase-order", "read", entity);
    }

    @Test
    void shouldThrowWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> guard.assertCurrentUserCanAccess("purchase-order", "read", new TestEntity()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldClearContextWhenPreviousContextIsNull() {
        when(permissionService.can(1L, "purchase-order", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", "read")).thenReturn("all");
        when(permissionService.getDataScopeOwnerUserIds(1L, "all")).thenReturn(Set.of());

        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(1L);

        assertThat(DataScopeContext.current()).isNull();

        guard.assertCanAccess(principal, "purchase-order", "read", entity);

        assertThat(DataScopeContext.current()).isNull();
    }

    @Test
    void shouldRestorePreviousContextWhenNotNull() {
        when(permissionService.can(1L, "purchase-order", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", "read")).thenReturn("all");
        when(permissionService.getDataScopeOwnerUserIds(1L, "all")).thenReturn(Set.of());

        DataScopeContext.set(5L, "sales-order", "self", Set.of(5L));

        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "admin", List.of());
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(1L);

        guard.assertCanAccess(principal, "purchase-order", "read", entity);

        assertThat(DataScopeContext.current()).isNotNull();
        assertThat(DataScopeContext.current().resource()).isEqualTo("sales-order");
        assertThat(DataScopeContext.current().scope()).isEqualTo("self");
    }

    private static class TestEntity extends AbstractAuditableEntity {
    }
}
