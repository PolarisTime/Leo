package com.leo.erp.system.role.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.role.domain.entity.RolePermission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTemplateServiceTest {

    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1);

    @Test
    void shouldListTemplates() {
        var service = new RoleTemplateService(idGenerator);

        var templates = service.listTemplates();

        assertThat(templates).hasSize(5);
        assertThat(templates.get(0).name()).isEqualTo("采购员");
        assertThat(templates.get(4).name()).isEqualTo("管理员");
    }

    @Test
    void shouldBuildPermissionsForSpecificTemplate() {
        var service = new RoleTemplateService(idGenerator);
        var template = new RoleTemplateService.Template("采购员", "desc", List.of(
                new RoleTemplateService.PermissionEntry("purchase-order", java.util.Set.of("read", "create"))
        ));

        var permissions = service.buildPermissions(1L, template);

        assertThat(permissions).hasSize(2);
        assertThat(permissions).extracting(RolePermission::getResourceCode)
                .containsOnly("purchase-order");
        assertThat(permissions).extracting(RolePermission::getActionCode)
                .containsExactlyInAnyOrder("read", "create");
    }

    @Test
    void shouldBuildPermissionsForAllResources_whenWildcardResource() {
        var service = new RoleTemplateService(idGenerator);
        var template = new RoleTemplateService.Template("管理员", "desc", List.of(
                new RoleTemplateService.PermissionEntry("*", java.util.Set.of("*"))
        ));

        var permissions = service.buildPermissions(1L, template);

        assertThat(permissions).isNotEmpty();
        assertThat(permissions.get(0).getRoleId()).isEqualTo(1L);
    }

    @Test
    void shouldAssignIdsToPermissions() {
        var service = new RoleTemplateService(idGenerator);
        var template = new RoleTemplateService.Template("采购员", "desc", List.of(
                new RoleTemplateService.PermissionEntry("purchase-order", java.util.Set.of("read"))
        ));

        var permissions = service.buildPermissions(1L, template);

        assertThat(permissions.get(0).getId()).isNotNull();
    }
}
