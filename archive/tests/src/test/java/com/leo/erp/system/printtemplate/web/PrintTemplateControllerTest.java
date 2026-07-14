package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintTemplateControllerTest {

    private final PrintTemplateService printTemplateService = mock(PrintTemplateService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ModulePermissionGuard modulePermissionGuard = new ModulePermissionGuard(permissionService);
    private final PrintTemplateController controller = new PrintTemplateController(printTemplateService, modulePermissionGuard);

    @Test
    void listReturnsTemplatesByBillType() {
        PrintTemplateResponse item = mock(PrintTemplateResponse.class);
        when(permissionService.can(1L, "sales-order", "print")).thenReturn(true);
        when(printTemplateService.listByBillType(eq("sales-order"))).thenReturn(List.of(item));

        ApiResponse<List<PrintTemplateResponse>> response = controller.list(principal(), "sales-order");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }

    @Test
    void listAllowsReadPermissionWhenPrintPermissionMissing() {
        PrintTemplateResponse item = mock(PrintTemplateResponse.class);
        when(permissionService.can(1L, "sales-order", "read")).thenReturn(true);
        when(printTemplateService.listByBillType(eq("sales-order"))).thenReturn(List.of(item));

        ApiResponse<List<PrintTemplateResponse>> response = controller.list(principal(), "sales-order");

        assertThat(response.code()).isEqualTo(0);
        verify(permissionService).can(1L, "sales-order", "print");
        verify(permissionService).can(1L, "sales-order", "read");
    }

    @Test
    void createReturnsCreatedTemplate() {
        PrintTemplateRequest request = request("sales-order");
        PrintTemplateResponse created = mock(PrintTemplateResponse.class);
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);
        when(printTemplateService.create(request)).thenReturn(created);

        ApiResponse<PrintTemplateResponse> response = controller.create(principal(), request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(printTemplateService).create(request);
    }

    @Test
    void updateReturnsUpdatedTemplate() {
        PrintTemplateRequest request = request("sales-order");
        PrintTemplateResponse updated = mock(PrintTemplateResponse.class);
        when(printTemplateService.getBillType(1L)).thenReturn("sales-order");
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);
        when(printTemplateService.update(1L, request)).thenReturn(updated);

        ApiResponse<PrintTemplateResponse> response = controller.update(principal(), 1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(printTemplateService).update(1L, request);
    }

    @Test
    void updateChecksCurrentBillTypeBeforeRequestedBillType() {
        PrintTemplateRequest request = request("sales-order");
        when(printTemplateService.getBillType(1L)).thenReturn("purchase-order");
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);

        assertThatThrownBy(() -> controller.update(principal(), 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
        verify(printTemplateService, never()).update(1L, request);
        verify(permissionService).can(1L, "purchase-order", "update");
    }

    @Test
    void updateChecksRequestedBillTypeWhenMovingTemplate() {
        PrintTemplateRequest request = request("sales-order");
        PrintTemplateResponse updated = mock(PrintTemplateResponse.class);
        when(printTemplateService.getBillType(1L)).thenReturn("purchase-order");
        when(permissionService.can(1L, "purchase-order", "update")).thenReturn(true);
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);
        when(printTemplateService.update(1L, request)).thenReturn(updated);

        ApiResponse<PrintTemplateResponse> response = controller.update(principal(), 1L, request);

        assertThat(response.code()).isEqualTo(0);
        verify(printTemplateService).update(1L, request);
    }

    @Test
    void uploadJsonReturnsUpdatedTemplate() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                "{\"page\":{}}".getBytes(StandardCharsets.UTF_8)
        );
        PrintTemplateResponse updated = mock(PrintTemplateResponse.class);
        when(printTemplateService.getBillType(1L)).thenReturn("sales-order");
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);
        when(printTemplateService.uploadJson(1L, file)).thenReturn(updated);

        ApiResponse<PrintTemplateResponse> response = controller.uploadJson(principal(), 1L, file);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("上传成功");
        assertThat(response.data()).isSameAs(updated);
        verify(printTemplateService).uploadJson(1L, file);
    }

    @Test
    void deleteCallsServiceDelete() {
        when(printTemplateService.getBillType(1L)).thenReturn("sales-order");
        when(permissionService.can(1L, "sales-order", "update")).thenReturn(true);

        ApiResponse<Void> response = controller.delete(principal(), 1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(printTemplateService).delete(1L);
    }

    @Test
    void createRejectsWhenPrincipalCannotManageTargetBillType() {
        PrintTemplateRequest request = request("sales-order");

        assertThatThrownBy(() -> controller.create(principal(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
        verify(printTemplateService, never()).create(request);
    }

    @Test
    void uploadJsonChecksTemplateBillTypeBeforeUpdating() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                "{\"page\":{}}".getBytes(StandardCharsets.UTF_8)
        );
        when(printTemplateService.getBillType(1L)).thenReturn("purchase-order");

        assertThatThrownBy(() -> controller.uploadJson(principal(), 1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无操作权限");
        verify(printTemplateService, never()).uploadJson(1L, file);
    }

    private SecurityPrincipal principal() {
        return new SecurityPrincipal(1L, "slc", "encoded", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private PrintTemplateRequest request(String billType) {
        return new PrintTemplateRequest(
                billType,
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('safe');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        );
    }
}
