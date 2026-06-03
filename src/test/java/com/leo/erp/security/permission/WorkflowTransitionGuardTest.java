package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTransitionGuardTest {

    private PermissionService permissionService;
    private SecurityPrincipal principal;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        when(permissionService.can(1L, "order", ResourcePermissionCatalog.AUDIT)).thenReturn(true);
        principal = SecurityPrincipal.authenticated(1L, "admin", List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPass_whenNextValueIsEmpty() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "", "audited"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPass_whenCurrentAndNextAreSame() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "draft", "audited"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPass_whenNotEnteringOrLeavingProtectedValues() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "completed", "audited"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPass_whenEnteringProtectedAndUserHasPermission() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "audited", "audited"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrow_whenEnteringProtectedAndUserHasNoPermission() {
        PermissionService noPermission = mock(PermissionService.class);
        when(noPermission.can(1L, "order", ResourcePermissionCatalog.AUDIT)).thenReturn(false);
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(noPermission));
        assertThatThrownBy(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "audited", "audited"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
    }

    @Test
    void shouldThrow_whenLeavingProtectedAndUserHasNoPermission() {
        PermissionService noPermission = mock(PermissionService.class);
        when(noPermission.can(1L, "order", ResourcePermissionCatalog.AUDIT)).thenReturn(false);
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(noPermission));
        assertThatThrownBy(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "audited", "draft", "audited"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
    }

    @Test
    void shouldPass_whenLeavingProtectedAndUserHasPermission() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "audited", "draft", "audited"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrow_whenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatThrownBy(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "audited", "audited"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldWorkWithVarargsProtectedValues() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", "draft", "audited", "audited", "signed"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNullProtectedValues() {
        WorkflowTransitionGuard guard = new WorkflowTransitionGuard(new ModulePermissionGuard(permissionService));
        assertThatCode(() -> guard.assertAuditPermissionForProtectedValue(
                "order", null, "audited", "audited"))
                .doesNotThrowAnyException();
    }
}
