package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePermissionCatalogTest {

    @Test
    void shouldFindKnownResource() {
        assertThat(ResourcePermissionCatalog.find("material")).isPresent();
        assertThat(ResourcePermissionCatalog.find("supplier")).isPresent();
        assertThat(ResourcePermissionCatalog.find("customer")).isPresent();
    }

    @Test
    void shouldNotFindUnknownResource() {
        assertThat(ResourcePermissionCatalog.find("unknown")).isEmpty();
    }

    @Test
    void shouldIdentifyKnownResource() {
        assertThat(ResourcePermissionCatalog.isKnownResource("material")).isTrue();
        assertThat(ResourcePermissionCatalog.isKnownResource("purchase-order")).isTrue();
        assertThat(ResourcePermissionCatalog.isKnownResource("ledger-adjustment")).isTrue();
    }

    @Test
    void shouldIdentifyUnknownResource() {
        assertThat(ResourcePermissionCatalog.isKnownResource("non-existing")).isFalse();
    }

    @Test
    void shouldIdentifyBusinessResource() {
        assertThat(ResourcePermissionCatalog.isBusinessResource("material")).isTrue();
        assertThat(ResourcePermissionCatalog.isBusinessResource("purchase-order")).isTrue();
        assertThat(ResourcePermissionCatalog.isBusinessResource("ledger-adjustment")).isTrue();
    }

    @Test
    void shouldIdentifyNonBusinessResource() {
        assertThat(ResourcePermissionCatalog.isBusinessResource("dashboard")).isFalse();
    }

    @Test
    void shouldCheckAllowedAction() {
        assertThat(ResourcePermissionCatalog.isAllowed("material", "read")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("material", "create")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("ledger-adjustment", "audit")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("material", "unknown")).isFalse();
    }

    @Test
    void shouldReturnResourceTitle() {
        assertThat(ResourcePermissionCatalog.resourceTitle("material")).isEqualTo("商品资料");
        assertThat(ResourcePermissionCatalog.resourceTitle("supplier")).isEqualTo("供应商资料");
    }

    @Test
    void shouldReturnActionCodeWhenUnknown() {
        assertThat(ResourcePermissionCatalog.resourceTitle("unknown")).isEqualTo("unknown");
    }

    @Test
    void shouldReturnActionTitle() {
        assertThat(ResourcePermissionCatalog.actionTitle("material", "read")).isEqualTo("查看");
        assertThat(ResourcePermissionCatalog.actionTitle("material", "create")).isEqualTo("新增");
    }

    @Test
    void shouldResolveResourceByMenuCode() {
        assertThat(ResourcePermissionCatalog.resolveResourceByMenuCode("material")).isPresent();
        assertThat(ResourcePermissionCatalog.resolveResourceByMenuCode("material-categories")).isPresent();
        assertThat(ResourcePermissionCatalog.resolveResourceByMenuCode("ledger-adjustment"))
                .contains("ledger-adjustment");
    }

    @Test
    void shouldResolveResourceByPath() {
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/material")).isPresent();
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/purchase-order/123")).isPresent();
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/ledger-adjustments/123"))
                .contains("ledger-adjustment");
    }

    @Test
    void shouldReturnEmptyForBlankPath() {
        assertThat(ResourcePermissionCatalog.resolveResourceByPath(null)).isEmpty();
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("")).isEmpty();
    }

    @Test
    void shouldResolveVisibleMenuCodes() {
        Map<String, Set<String>> permissionMap = Map.of(
                "material", Set.of("read", "create"),
                "supplier", Set.of("read"),
                "ledger-adjustment", Set.of("read")
        );

        Set<String> menuCodes = ResourcePermissionCatalog.resolveVisibleMenuCodes(permissionMap);

        assertThat(menuCodes).contains("material", "supplier", "ledger-adjustment");
    }

    @Test
    void shouldReturnEmptyMenuCodesForNullMap() {
        assertThat(ResourcePermissionCatalog.resolveVisibleMenuCodes(null)).isEmpty();
        assertThat(ResourcePermissionCatalog.resolveVisibleMenuCodes(Map.of())).isEmpty();
    }

    @Test
    void shouldNotIncludeResourcesWithoutReadPermission() {
        Map<String, Set<String>> permissionMap = Map.of(
                "material", Set.of("create")
        );

        Set<String> menuCodes = ResourcePermissionCatalog.resolveVisibleMenuCodes(permissionMap);

        assertThat(menuCodes).doesNotContain("material");
    }

    @Test
    void shouldNormalizeActionAliases() {
        assertThat(ResourcePermissionCatalog.normalizeAction("view")).isEqualTo("read");
        assertThat(ResourcePermissionCatalog.normalizeAction("edit")).isEqualTo("update");
        assertThat(ResourcePermissionCatalog.normalizeAction("read")).isEqualTo("read");
    }

    @Test
    void shouldNormalizeDataScope() {
        assertThat(ResourcePermissionCatalog.normalizeDataScope("all")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("全部")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("department")).isEqualTo("department");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("本部门")).isEqualTo("department");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("self")).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("本人")).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope(null)).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("")).isEqualTo("self");
    }

    @Test
    void shouldReturnBroaderDataScope() {
        assertThat(ResourcePermissionCatalog.broaderDataScope("all", "self")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.broaderDataScope("self", "all")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.broaderDataScope("department", "self")).isEqualTo("department");
    }

    @Test
    void shouldHaveAllowedResourceCodes() {
        Set<String> codes = ResourcePermissionCatalog.allowedResourceCodes();
        assertThat(codes).isNotEmpty();
        assertThat(codes).contains("material", "supplier", "customer", "ledger-adjustment");
    }

    @Test
    void shouldReturnActionsForResource() {
        var actions = ResourcePermissionCatalog.actionsForResource("material");
        assertThat(actions).contains("read", "create", "update", "delete", "audit", "export", "print");
    }

    @Test
    void shouldReturnEmptyActionsForUnknownResource() {
        assertThat(ResourcePermissionCatalog.actionsForResource("unknown")).isEmpty();
    }

    @Test
    void shouldHaveActionConstants() {
        assertThat(ResourcePermissionCatalog.READ).isEqualTo("read");
        assertThat(ResourcePermissionCatalog.CREATE).isEqualTo("create");
        assertThat(ResourcePermissionCatalog.UPDATE).isEqualTo("update");
        assertThat(ResourcePermissionCatalog.DELETE).isEqualTo("delete");
        assertThat(ResourcePermissionCatalog.AUDIT).isEqualTo("audit");
        assertThat(ResourcePermissionCatalog.EXPORT).isEqualTo("export");
        assertThat(ResourcePermissionCatalog.PRINT).isEqualTo("print");
        assertThat(ResourcePermissionCatalog.MANAGE_PERMISSIONS).isEqualTo("manage_permissions");
    }

    @Test
    void shouldHaveScopeConstants() {
        assertThat(ResourcePermissionCatalog.SCOPE_ALL).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.SCOPE_DEPARTMENT).isEqualTo("department");
        assertThat(ResourcePermissionCatalog.SCOPE_CUSTOM).isEqualTo("custom");
        assertThat(ResourcePermissionCatalog.SCOPE_SELF).isEqualTo("self");
    }

    @Test
    void shouldReturnAllResourceLabels() {
        var labels = ResourcePermissionCatalog.getAllResourceLabels();
        assertThat(labels).isNotEmpty();
        assertThat(labels).containsEntry("material", "商品资料");
    }

    @Test
    void shouldReturnAllActionLabels() {
        var labels = ResourcePermissionCatalog.getAllActionLabels();
        assertThat(labels).isNotEmpty();
        assertThat(labels).containsEntry("read", "查看");
        assertThat(labels).containsEntry("create", "新增");
    }
}
