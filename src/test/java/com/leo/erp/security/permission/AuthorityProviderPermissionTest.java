package com.leo.erp.security.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * 默认权限提供者测试：必须为任意登录用户返回全部权限码，保证既有单账号全量可用。
 */
class AuthorityProviderPermissionTest {

    private final GrantAllAuthorityProvider provider = new GrantAllAuthorityProvider();

    @Test
    void authoritiesFor_shouldReturnEntireCatalog() {
        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(1L, "admin", 0L));

        assertThat(authorities).containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
    }

    @Test
    void authoritiesFor_shouldCoverPilotModules() {
        Collection<String> authorities = provider.authoritiesFor(
                SecurityPrincipal.authenticated(1L, "admin", 0L));

        assertThat(authorities).contains(
                PermissionCodes.SALES_RETURNS_READ,
                PermissionCodes.SALES_RETURNS_UPDATE,
                PermissionCodes.SALES_RETURNS_AUDIT,
                PermissionCodes.INVENTORY_READ,
                PermissionCodes.INVENTORY_BACKFILL,
                PermissionCodes.MATERIALS_READ,
                PermissionCodes.MATERIALS_UPDATE,
                PermissionCodes.MATERIAL_IMPORTS_IMPORT,
                PermissionCodes.IMPORT_BATCHES_ROLLBACK,
                PermissionCodes.CUSTOMER_STATEMENTS_READ,
                PermissionCodes.CUSTOMER_STATEMENTS_UPDATE,
                PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM,
                PermissionCodes.SYSTEM_ADMIN
        );
    }

    @Test
    void authoritiesFor_shouldWorkForSystemPrincipal() {
        assertThat(provider.authoritiesFor(SecurityPrincipal.system()))
                .containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
    }
}
