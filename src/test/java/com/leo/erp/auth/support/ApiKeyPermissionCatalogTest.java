package com.leo.erp.auth.support;

import com.leo.erp.security.permission.ResourcePermissionCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePermissionCatalogPathTest {

    @Test
    void shouldResolveRoleResourceForEditorPageAndPermissionEndpoints() {
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-action-editor"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-settings/1/permissions"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-settings/1/actions/history"))
                .contains("role");
    }

    @Test
    void shouldResolveRoleResourceForCrudEndpoints() {
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-settings"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-settings/1"))
                .contains("role");
    }

    @Test
    void shouldRequireReadPermissionForVisibleMenus() {
        assertThat(ResourcePermissionCatalog.resolveVisibleMenuCodes(Map.of("role", Set.of("manage_permissions"))))
                .isEmpty();
        assertThat(ResourcePermissionCatalog.resolveVisibleMenuCodes(Map.of("access-control", Set.of("read"))))
                .contains("access-control");
        assertThat(ResourcePermissionCatalog.resolveVisibleMenuCodes(Map.of("user-account", Set.of("read"))))
                .isEmpty();
    }
}
