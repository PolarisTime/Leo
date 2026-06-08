package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModulePermissionGuardTest {

    private final PermissionService permissionService = mock(PermissionService.class);
    private final ModulePermissionGuard guard = new ModulePermissionGuard(permissionService);

    @Test
    void shouldThrowWhenPrincipalIsNull() {
        assertThatThrownBy(() -> guard.requirePermission(null, "material", "read"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void shouldThrowWhenModuleKeyIsEmpty() {
        SecurityPrincipal principal = createPrincipal(1L);
        assertThatThrownBy(() -> guard.requirePermission(principal, "", "read"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldThrowWhenModuleKeyIsNull() {
        SecurityPrincipal principal = createPrincipal(1L);
        assertThatThrownBy(() -> guard.requirePermission(principal, null, "read"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldThrowWhenNoPermission() {
        SecurityPrincipal principal = createPrincipal(1L);
        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> guard.requirePermission(principal, "material", "read"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
    }

    @Test
    void shouldReturnModuleKeyWhenHasPermission() {
        SecurityPrincipal principal = createPrincipal(1L);
        when(permissionService.can(1L, "material", "read")).thenReturn(true);

        String result = guard.requirePermission(principal, "material", "read");

        assertThat(result).isEqualTo("material");
    }

    @Test
    void shouldReturnPermissionCheck() {
        SecurityPrincipal principal = createPrincipal(1L);
        when(permissionService.can(1L, "material", "read")).thenReturn(true);

        ModulePermissionGuard.PermissionCheck check = guard.requireResourcePermission(principal, "material", "read");

        assertThat(check.moduleKey()).isEqualTo("material");
        assertThat(check.resource()).isEqualTo("material");
        assertThat(check.action()).isEqualTo("read");
    }

    @Test
    void shouldAllowAnyConfiguredAction() {
        SecurityPrincipal principal = createPrincipal(1L);
        when(permissionService.can(1L, "sales-order", "read")).thenReturn(true);

        ModulePermissionGuard.PermissionCheck check = guard.requireResourcePermissionAny(
                principal,
                "sales-order",
                "print",
                "read"
        );

        assertThat(check.resource()).isEqualTo("sales-order");
        assertThat(check.action()).isEqualTo("read");
    }

    @Test
    void shouldThrowWhenNoAnyActionPermission() {
        SecurityPrincipal principal = createPrincipal(1L);
        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> guard.requireResourcePermissionAny(principal, "sales-order", "print", "read"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
    }

    private SecurityPrincipal createPrincipal(Long id) {
        return new SecurityPrincipal(id, "user", "encoded", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
