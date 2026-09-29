package com.leo.erp.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RBAC0 权限提供者测试：按角色聚合、通配传递、系统主体全量、无角色为空，
 * 以及缓存命中/未命中/空集合缓存的行为。
 */
@ExtendWith(MockitoExtension.class)
class RoleBasedAuthorityProviderTest {

    private static final Long USER_ID = 7L;
    private static final long CREDENTIAL_VERSION = 0L;

    @Mock
    private SysRolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private RoleBasedAuthorityProvider provider;

    @Test
    void authoritiesFor_shouldAggregateRolePermissions() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION)).thenReturn(Optional.empty());
        when(rolePermissionRepository.findPermissionCodesByUserId(USER_ID, StatusConstants.NORMAL))
                .thenReturn(List.of("roles:read", "roles:write", "sales-orders:read", "sales-orders:*"));

        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(USER_ID, "tester", CREDENTIAL_VERSION));

        assertThat(authorities).containsExactlyInAnyOrder(
                "roles:read", "roles:write", "sales-orders:read", "sales-orders:*");
    }

    @Test
    void authoritiesFor_shouldPassThroughWildcard() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION)).thenReturn(Optional.empty());
        when(rolePermissionRepository.findPermissionCodesByUserId(USER_ID, StatusConstants.NORMAL))
                .thenReturn(List.of(PermissionCodes.WILDCARD));

        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(USER_ID, "super", CREDENTIAL_VERSION));

        assertThat(authorities).containsExactly(PermissionCodes.WILDCARD);
    }

    @Test
    void authoritiesFor_shouldReturnEmptyWhenUserHasNoRolePermissions() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION)).thenReturn(Optional.empty());
        when(rolePermissionRepository.findPermissionCodesByUserId(USER_ID, StatusConstants.NORMAL))
                .thenReturn(List.of());

        assertThat(provider.authoritiesFor(
                SecurityPrincipal.authenticated(USER_ID, "tester", CREDENTIAL_VERSION))).isEmpty();
    }

    /** 缓存未命中时，回源结果必须写回缓存，否则缓存永远无法生效。 */
    @Test
    void authoritiesFor_cacheMissShouldPopulateCache() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION)).thenReturn(Optional.empty());
        List<String> codes = List.of("roles:read");
        when(rolePermissionRepository.findPermissionCodesByUserId(USER_ID, StatusConstants.NORMAL))
                .thenReturn(codes);

        provider.authoritiesFor(SecurityPrincipal.authenticated(USER_ID, "tester", CREDENTIAL_VERSION));

        verify(permissionCacheService).put(USER_ID, CREDENTIAL_VERSION, codes);
    }

    /** 缓存命中时不得再查库——这是本次优化的核心收益。 */
    @Test
    void authoritiesFor_cacheHitShouldNotQueryDatabase() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION))
                .thenReturn(Optional.of(List.of("roles:read", "sales-orders:read")));

        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(USER_ID, "tester", CREDENTIAL_VERSION));

        assertThat(authorities).containsExactlyInAnyOrder("roles:read", "sales-orders:read");
        verifyNoInteractions(rolePermissionRepository);
    }

    /**
     * 空权限集合也要能命中缓存：无角色用户的每次请求都回源会造成无谓的数据库压力，
     * 且这类用户往往是 403 排查的高频场景。
     */
    @Test
    void authoritiesFor_emptyCachedSetShouldNotQueryDatabase() {
        when(permissionCacheService.get(USER_ID, CREDENTIAL_VERSION)).thenReturn(Optional.of(List.of()));

        assertThat(provider.authoritiesFor(
                SecurityPrincipal.authenticated(USER_ID, "tester", CREDENTIAL_VERSION))).isEmpty();
        verifyNoInteractions(rolePermissionRepository);
    }

    /** 凭据版本参与缓存 key：版本变化意味着旧缓存不可命中，必须回源。 */
    @Test
    void authoritiesFor_shouldPassCredentialVersionToCache() {
        when(permissionCacheService.get(USER_ID, 5L)).thenReturn(Optional.empty());
        when(rolePermissionRepository.findPermissionCodesByUserId(USER_ID, StatusConstants.NORMAL))
                .thenReturn(List.of("roles:read"));

        provider.authoritiesFor(SecurityPrincipal.authenticated(USER_ID, "tester", 5L));

        verify(permissionCacheService).get(USER_ID, 5L);
        verify(permissionCacheService).put(USER_ID, 5L, List.of("roles:read"));
    }

    @Test
    void authoritiesFor_systemPrincipalShouldReturnEntireCatalogWithoutQuery() {
        assertThat(provider.authoritiesFor(SecurityPrincipal.system()))
                .containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
        verifyNoInteractions(rolePermissionRepository, permissionCacheService);
    }

    @Test
    void authoritiesFor_nullPrincipalShouldReturnEmpty() {
        assertThat(provider.authoritiesFor(null)).isEmpty();
        verifyNoInteractions(rolePermissionRepository, permissionCacheService);
    }
}
