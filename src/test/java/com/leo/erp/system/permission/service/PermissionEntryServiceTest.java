package com.leo.erp.system.permission.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEntryServiceTest {

    @Test
    void shouldBuildPermissionDefinitionsFromResourceCatalog() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(PageQuery.of(0, 20, "id", "asc"), "角色").getContent();

        assertThat(records)
                .extracting(
                        PermissionEntryResponse::permissionCode,
                        PermissionEntryResponse::permissionName,
                        PermissionEntryResponse::moduleName,
                        PermissionEntryResponse::permissionType,
                        PermissionEntryResponse::actionName,
                        PermissionEntryResponse::resourceKey
                )
                .contains(
                        org.assertj.core.groups.Tuple.tuple("role:read", "角色查看", "系统", "资源权限", "查看", "role"),
                        org.assertj.core.groups.Tuple.tuple("role:update", "角色编辑", "系统", "操作权限", "编辑", "role"),
                        org.assertj.core.groups.Tuple.tuple("role:manage_permissions", "角色配置权限", "系统", "操作权限", "配置权限", "role")
                );

        Long updatePermissionId = records.stream()
                .filter(record -> "role:update".equals(record.permissionCode()))
                .findFirst()
                .map(PermissionEntryResponse::id)
                .orElseThrow();
        assertThat(service.detail(updatePermissionId).scopeName()).isEqualTo("全部");
    }

    @Test
    void shouldCreateServiceThroughAutowiredConstructor() {
        PermissionEntryService service = new PermissionEntryService(null, null, null);

        assertThat(service.catalog()).isNotEmpty();
    }

    @Test
    void shouldReturnAllEntriesWhenKeywordIsBlank() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        long total = service.page(PageQuery.of(0, 200, "id", "asc"), null).getTotalElements();

        assertThat(service.page(PageQuery.of(0, 200, "id", "asc"), "   ").getTotalElements())
                .isEqualTo(total);
    }

    @Test
    void shouldSortByRemainingTextFieldsAscending() {
        assertSortedAscending("moduleName", PermissionEntryResponse::moduleName);
        assertSortedAscending("permissionType", PermissionEntryResponse::permissionType);
        assertSortedAscending("actionName", PermissionEntryResponse::actionName);
    }

    @Test
    void shouldMatchEntriesByLaterSearchFields() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        assertThat(matches(service, entry(null, "name", "module-hit", "type", "action", "resource"), "module-hit"))
                .isTrue();
        assertThat(matches(service, entry("code", "name", "module", "type-hit", "action", "resource"), "type-hit"))
                .isTrue();
        assertThat(matches(service, entry("code", "name", "module", "type", "action-hit", "resource"), "action-hit"))
                .isTrue();
        assertThat(matches(service, entry("code", "name", "module", "type", "action", "resource-hit"), "resource-hit"))
                .isTrue();
        assertThat(matches(service, entry(null, null, null, null, null, null), "missing"))
                .isFalse();
    }

    private void assertSortedAscending(String sortBy, Function<PermissionEntryResponse, String> extractor) {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<String> values = service.page(PageQuery.of(0, 200, sortBy, "asc"), null)
                .getContent()
                .stream()
                .map(extractor)
                .toList();

        assertThat(values).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    private PermissionEntryResponse entry(String permissionCode,
                                          String permissionName,
                                          String moduleName,
                                          String permissionType,
                                          String actionName,
                                          String resourceKey) {
        return new PermissionEntryResponse(
                1L,
                permissionCode,
                permissionName,
                moduleName,
                permissionType,
                actionName,
                "全部",
                resourceKey,
                "正常",
                "系统资源动作定义"
        );
    }

    private boolean matches(PermissionEntryService service,
                            PermissionEntryResponse entry,
                            String keyword) {
        try {
            Method method = PermissionEntryService.class.getDeclaredMethod(
                    "matches",
                    PermissionEntryResponse.class,
                    String.class
            );
            method.setAccessible(true);
            return (boolean) method.invoke(service, entry, keyword);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
