package com.leo.erp.system.permission.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
