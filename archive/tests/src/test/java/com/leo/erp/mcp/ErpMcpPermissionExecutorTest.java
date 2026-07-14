package com.leo.erp.mcp;

import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpMcpPermissionExecutorTest {

    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ErpMcpPermissionExecutor executor =
            new ErpMcpPermissionExecutor(modulePermissionGuard, permissionService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void shouldSetBusinessDataScopeAndClearWhenNoPreviousContext() {
        SecurityPrincipal principal = authenticate(7L);
        when(modulePermissionGuard.requireResourcePermission(principal, "material", ResourcePermissionCatalog.READ))
                .thenReturn(new ModulePermissionGuard.PermissionCheck("material", "material", ResourcePermissionCatalog.READ));
        when(permissionService.getUserDataScope(7L, "material", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        when(permissionService.getDataScopeOwnerUserIds(7L, ResourcePermissionCatalog.SCOPE_DEPARTMENT))
                .thenReturn(Set.of(7L, 8L));

        String result = executor.read("material", () -> {
            DataScopeContext.Context context = DataScopeContext.current();
            assertThat(context.userId()).isEqualTo(7L);
            assertThat(context.resource()).isEqualTo("material");
            assertThat(context.scope()).isEqualTo(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
            assertThat(context.ownerUserIds()).containsExactlyInAnyOrder(7L, 8L);
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(DataScopeContext.current()).isNull();
        verify(permissionService).getUserDataScope(7L, "material", ResourcePermissionCatalog.READ);
        verify(permissionService).getDataScopeOwnerUserIds(7L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);
    }

    @Test
    void shouldUseAllScopeForNonBusinessResourceAndRestorePreviousContext() {
        SecurityPrincipal principal = authenticate(9L);
        DataScopeContext.set(99L, "supplier", ResourcePermissionCatalog.SCOPE_SELF, Set.of(99L));
        DataScopeContext.Context previous = DataScopeContext.current();
        when(modulePermissionGuard.requireResourcePermission(principal, "print-template", ResourcePermissionCatalog.PRINT))
                .thenReturn(new ModulePermissionGuard.PermissionCheck(
                        "print-template",
                        "print-template",
                        ResourcePermissionCatalog.PRINT
                ));
        when(permissionService.getDataScopeOwnerUserIds(9L, ResourcePermissionCatalog.SCOPE_ALL))
                .thenReturn(Set.of(9L));

        String result = executor.print("print-template", () -> {
            DataScopeContext.Context context = DataScopeContext.current();
            assertThat(context.userId()).isEqualTo(9L);
            assertThat(context.resource()).isEqualTo("print-template");
            assertThat(context.scope()).isEqualTo(ResourcePermissionCatalog.SCOPE_ALL);
            assertThat(DataScopeContext.allowedOwnerUserIds()).isNull();
            return "printed";
        });

        assertThat(result).isEqualTo("printed");
        assertThat(DataScopeContext.current()).isEqualTo(previous);
        verify(permissionService, never()).getUserDataScope(9L, "print-template", ResourcePermissionCatalog.PRINT);
        verify(permissionService).getDataScopeOwnerUserIds(9L, ResourcePermissionCatalog.SCOPE_ALL);
    }

    @Test
    void shouldRestorePreviousContextWhenActionThrows() {
        SecurityPrincipal principal = authenticate(7L);
        DataScopeContext.set(42L, "customer", ResourcePermissionCatalog.SCOPE_SELF, Set.of(42L));
        DataScopeContext.Context previous = DataScopeContext.current();
        when(modulePermissionGuard.requireResourcePermission(principal, "customer", ResourcePermissionCatalog.READ))
                .thenReturn(new ModulePermissionGuard.PermissionCheck("customer", "customer", ResourcePermissionCatalog.READ));
        when(permissionService.getUserDataScope(7L, "customer", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(7L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(7L));

        assertThatThrownBy(() -> executor.read("customer", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(DataScopeContext.current()).isEqualTo(previous);
    }

    @Test
    void shouldRejectMissingSecurityPrincipal() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> executor.read("material", () -> "ignored"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("未登录");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of())
        );

        assertThatThrownBy(() -> executor.read("material", () -> "ignored"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("未登录");
    }

    private SecurityPrincipal authenticate(Long userId) {
        SecurityPrincipal principal = new SecurityPrincipal(
                userId,
                "mcp-user",
                "",
                true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        return principal;
    }
}
