package com.leo.erp.security.permission;

import com.leo.erp.system.role.domain.entity.RolePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
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
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/company-settings/options"))
                .contains("company-setting");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/company-settings/current"))
                .contains("company-setting");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-setting/1/permission"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-setting/1/action/edit"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/general-setting/upload-rule/edit"))
                .contains("general-setting");
    }

    @Test
    void shouldResolvePluralApiRoutesByPath() {
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/departments/options"))
                .contains("department");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/warehouses/options"))
                .contains("warehouse");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/customers/options"))
                .contains("customer");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/suppliers/options"))
                .contains("supplier");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/carriers/options"))
                .contains("carrier");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/material-categories/options"))
                .contains("material");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/materials/grades"))
                .contains("material");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/auth/api-keys/resource-options"))
                .contains("api-key");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/role-settings/permission-options"))
                .contains("role");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/purchase-orders/import-candidates"))
                .contains("purchase-order");
        assertThat(ResourcePermissionCatalog.resolveResourceByPath("/supplier-statements/candidates"))
                .contains("supplier-statement");
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
    void shouldIgnoreNullActionsWhenResolvingVisibleMenuCodes() {
        Map<String, Set<String>> permissionMap = new java.util.HashMap<>();
        permissionMap.put("material", null);
        permissionMap.put("unknown", Set.of("read"));

        Set<String> menuCodes = ResourcePermissionCatalog.resolveVisibleMenuCodes(permissionMap);

        assertThat(menuCodes).isEmpty();
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
        assertThat(ResourcePermissionCatalog.normalizeDataScope("全部数据")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("全部")).isEqualTo("all");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("department")).isEqualTo("department");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("本部门")).isEqualTo("department");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("custom")).isEqualTo("custom");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("自定义范围")).isEqualTo("custom");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("self")).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("本人")).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope(null)).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("")).isEqualTo("self");
        assertThat(ResourcePermissionCatalog.normalizeDataScope("未知")).isEqualTo("self");
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
    void shouldExposeEntriesAndActionOptions() {
        assertThat(ResourcePermissionCatalog.entries()).isNotEmpty();
        assertThat(ResourcePermissionCatalog.actionOptions())
                .extracting(ResourcePermissionCatalog.ActionOption::code)
                .contains("read", "create", "update", "delete");
    }

    @Test
    void shouldReturnActionsForResource() {
        var actions = ResourcePermissionCatalog.actionsForResource("material");
        assertThat(actions).contains("read", "create", "update", "delete", "audit", "export", "print");
    }

    @Test
    void shouldKeepReceivablePayableReadOnlyReportActions() {
        var actions = ResourcePermissionCatalog.actionsForResource("receivable-payable");

        assertThat(actions).containsExactly("read", "export", "print");
        assertThat(actions).doesNotContain("create", "update", "delete", "audit");
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "read")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "export")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "print")).isTrue();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "create")).isFalse();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "update")).isFalse();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "delete")).isFalse();
        assertThat(ResourcePermissionCatalog.isAllowed("receivable-payable", "audit")).isFalse();
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

    @Test
    void shouldNormalizeResourcesAndActions() {
        assertThat(ResourcePermissionCatalog.normalizeResource(" /Material ")).isEqualTo("material");
        assertThat(ResourcePermissionCatalog.normalizeResource(null)).isEmpty();
        assertThat(ResourcePermissionCatalog.isKnownAction("view")).isTrue();
        assertThat(ResourcePermissionCatalog.isKnownAction("missing-action")).isFalse();
        assertThat(ResourcePermissionCatalog.actionTitle("unknown", "view")).isEqualTo("read");
    }

    @Test
    void shouldBuildPermissionSummaryForEmptySmallAndLargeCollections() {
        assertThat(ResourcePermissionCatalog.buildPermissionSummary(null)).isEmpty();
        assertThat(ResourcePermissionCatalog.buildPermissionSummary(List.of())).isEmpty();

        assertThat(ResourcePermissionCatalog.buildPermissionSummary(List.of(
                permission("material", "read"),
                permission("material", "read"),
                permission("supplier", "edit")
        ))).isEqualTo("商品资料-查看、供应商资料-编辑");

        assertThat(ResourcePermissionCatalog.buildPermissionSummary(List.of(
                permission("material", "read"),
                permission("supplier", "read"),
                permission("customer", "read"),
                permission("project", "read"),
                permission("carrier", "read"),
                permission("warehouse", "read"),
                permission("purchase-order", "read")
        ))).endsWith(" 等7项");
    }

    @Test
    void shouldInstantiatePrivateConstructorForCoverage() throws Exception {
        Constructor<ResourcePermissionCatalog> constructor = ResourcePermissionCatalog.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }

    @Test
    void shouldCalculateLongestPrefixLength() throws Exception {
        Method method = ResourcePermissionCatalog.class.getDeclaredMethod("longestPrefixLength", List.class, String.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, List.of("/material"), "/material")).isEqualTo("/material".length());
        assertThat(method.invoke(null, List.of("/material", "/material-categories"), "/material-categories/1"))
                .isEqualTo("/material-categories".length());
        assertThat(method.invoke(null, List.of("/supplier"), "/material")).isEqualTo(0);
    }

    private RolePermission permission(String resourceCode, String actionCode) {
        RolePermission permission = new RolePermission();
        permission.setResourceCode(resourceCode);
        permission.setActionCode(actionCode);
        return permission;
    }
}
