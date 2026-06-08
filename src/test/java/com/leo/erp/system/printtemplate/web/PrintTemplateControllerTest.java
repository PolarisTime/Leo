package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintTemplateService;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        PrintTemplateRequest request = mock(PrintTemplateRequest.class);
        PrintTemplateResponse created = mock(PrintTemplateResponse.class);
        when(printTemplateService.create(request)).thenReturn(created);

        ApiResponse<PrintTemplateResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(printTemplateService).create(request);
    }

    @Test
    void updateReturnsUpdatedTemplate() {
        PrintTemplateRequest request = mock(PrintTemplateRequest.class);
        PrintTemplateResponse updated = mock(PrintTemplateResponse.class);
        when(printTemplateService.update(1L, request)).thenReturn(updated);

        ApiResponse<PrintTemplateResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(printTemplateService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(printTemplateService).delete(1L);
    }

    private SecurityPrincipal principal() {
        return new SecurityPrincipal(1L, "slc", "encoded", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
