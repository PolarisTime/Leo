package com.leo.erp.system.role.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import com.leo.erp.system.role.service.RoleSettingService;
import com.leo.erp.system.role.service.RoleTemplateService;
import com.leo.erp.system.role.web.dto.RolePermissionItem;
import com.leo.erp.system.role.web.dto.RoleSettingRequest;
import com.leo.erp.system.role.web.dto.RoleSettingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleSettingControllerTest {

    private final RoleSettingService roleSettingService = mock(RoleSettingService.class);
    private final RoleTemplateService roleTemplateService = mock(RoleTemplateService.class);
    private final RoleSettingController controller = new RoleSettingController(roleSettingService, roleTemplateService);

    @Test
    void pageReturnsPaginatedRoles() {
        RoleSettingResponse item = mock(RoleSettingResponse.class);
        Page<RoleSettingResponse> page = new PageImpl<>(List.of(item));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(roleSettingService.page(any(), eq("test"), eq("active"))).thenReturn(page);

        ApiResponse<PageResponse<RoleSettingResponse>> response = controller.page(query, "test", "active");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsRoleById() {
        RoleSettingResponse role = mock(RoleSettingResponse.class);
        when(roleSettingService.detail(1L)).thenReturn(role);

        ApiResponse<RoleSettingResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(role);
    }

    @Test
    void createReturnsCreatedRole() {
        RoleSettingRequest request = mock(RoleSettingRequest.class);
        RoleSettingResponse created = mock(RoleSettingResponse.class);
        when(roleSettingService.create(request)).thenReturn(created);

        ApiResponse<RoleSettingResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(roleSettingService).create(request);
    }

    @Test
    void updateReturnsUpdatedRole() {
        RoleSettingRequest request = mock(RoleSettingRequest.class);
        RoleSettingResponse updated = mock(RoleSettingResponse.class);
        when(roleSettingService.update(1L, request)).thenReturn(updated);

        ApiResponse<RoleSettingResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(roleSettingService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(roleSettingService).delete(1L);
    }

    @Test
    void getRolePermissionsReturnsPermissions() {
        RolePermissionItem item = mock(RolePermissionItem.class);
        when(roleSettingService.getRolePermissions(1L)).thenReturn(List.of(item));

        ApiResponse<List<RolePermissionItem>> response = controller.getRolePermissions(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void listPermissionOptionsReturnsOptions() {
        MenuTreeResponse item = mock(MenuTreeResponse.class);
        when(roleSettingService.listPermissionOptions()).thenReturn(List.of(item));

        ApiResponse<List<MenuTreeResponse>> response = controller.listPermissionOptions();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void listRoleTemplatesReturnsTemplates() {
        RoleTemplateService.Template item = mock(RoleTemplateService.Template.class);
        when(roleTemplateService.listTemplates()).thenReturn(List.of(item));

        ApiResponse<List<RoleTemplateService.Template>> response = controller.listRoleTemplates();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void saveRolePermissionsCallsService() {
        RolePermissionItem item = mock(RolePermissionItem.class);
        List<RolePermissionItem> permissions = List.of(item);

        ApiResponse<Void> response = controller.saveRolePermissions(1L, permissions);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("权限保存成功");
        verify(roleSettingService).saveRolePermissions(1L, permissions);
    }
}
