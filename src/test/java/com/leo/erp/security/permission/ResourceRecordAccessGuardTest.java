package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceRecordAccessGuardTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void shouldRejectOutOfScopeRecordAndRestorePreviousContext() {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "customer-statement", "read")).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "customer-statement", "read")).thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF)).thenReturn(Set.of(1L));
        ResourceRecordAccessGuard guard = new ResourceRecordAccessGuard(
                new ModulePermissionGuard(permissionService),
                permissionService
        );
        CustomerStatement statement = new CustomerStatement();
        statement.setCreatedBy(2L);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "tester", List.of());

        DataScopeContext.set(1L, "receipt", ResourcePermissionCatalog.SCOPE_ALL);

        assertThatThrownBy(() -> guard.assertCanAccess(principal, "customer-statements", "read", statement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");

        assertThat(DataScopeContext.current()).isNotNull();
        assertThat(DataScopeContext.current().resource()).isEqualTo("receipt");
        assertThat(DataScopeContext.current().scope()).isEqualTo(ResourcePermissionCatalog.SCOPE_ALL);
    }
}
