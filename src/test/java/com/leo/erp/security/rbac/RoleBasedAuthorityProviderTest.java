package com.leo.erp.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RBAC0 权限提供者测试：按角色聚合、通配传递、系统主体全量、无角色为空。
 */
@ExtendWith(MockitoExtension.class)
class RoleBasedAuthorityProviderTest {

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private RoleBasedAuthorityProvider provider;

    @Test
    void authoritiesFor_shouldAggregateRolePermissions() {
        when(rolePermissionRepository.findPermissionCodesByUserId(7L, StatusConstants.NORMAL))
                .thenReturn(List.of("roles:read", "roles:write", "sales-orders:read", "sales-orders:*"));

        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(7L, "tester", 0L));

        assertThat(authorities).containsExactlyInAnyOrder(
                "roles:read", "roles:write", "sales-orders:read", "sales-orders:*");
    }

    @Test
    void authoritiesFor_shouldPassThroughWildcard() {
        when(rolePermissionRepository.findPermissionCodesByUserId(7L, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));

        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(7L, "super", 0L));

        assertThat(authorities).containsExactly(PermissionCodes.WILDCARD);
    }

    @Test
    void authoritiesFor_shouldReturnEmptyWhenUserHasNoRolePermissions() {
        when(rolePermissionRepository.findPermissionCodesByUserId(7L, StatusConstants.NORMAL))
                .thenReturn(List.of());

        assertThat(provider.authoritiesFor(SecurityPrincipal.authenticated(7L, "tester", 0L))).isEmpty();
    }

    @Test
    void authoritiesFor_systemPrincipalShouldReturnEntireCatalogWithoutQuery() {
        assertThat(provider.authoritiesFor(SecurityPrincipal.system()))
                .containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
        verifyNoInteractions(rolePermissionRepository);
    }

    @Test
    void authoritiesFor_nullPrincipalShouldReturnEmpty() {
        assertThat(provider.authoritiesFor(null)).isEmpty();
        verifyNoInteractions(rolePermissionRepository);
    }
}
