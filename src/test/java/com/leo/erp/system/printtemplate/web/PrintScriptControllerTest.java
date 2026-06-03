package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintPdfFormService;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintScriptControllerTest {

    private final PrintScriptService printScriptService = mock(PrintScriptService.class);
    private final PrintPdfFormService printPdfFormService = mock(PrintPdfFormService.class);
    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final PrintScriptController controller = new PrintScriptController(printScriptService, printPdfFormService, modulePermissionGuard);

    @Test
    void fromRecordReturnsGeneratedResult() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("templateId", "template-1");
        payload.put("recordId", "1");

        Map<String, Object> result = new HashMap<>();
        result.put("templateType", "COORD");
        result.put("data", "test");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.generateFromRecord(eq("template-1"), eq("sales-order"), eq(1L))).thenReturn(result);

        ApiResponse<Map<String, Object>> response = controller.fromRecord(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(result);
        verify(printScriptService).generateFromRecord("template-1", "sales-order", 1L);
    }

    @Test
    void fromRecordWithPdfFormReturnsPdfBase64() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("templateId", "template-1");
        payload.put("recordId", "1");

        Map<String, Object> result = new HashMap<>();
        result.put("templateType", "PDF_FORM");
        result.put("templateName", "Test Template");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.generateFromRecord(eq("template-1"), eq("sales-order"), eq(1L))).thenReturn(result);
        when(printPdfFormService.generateFromPayload(any())).thenReturn("pdf-content".getBytes());

        ApiResponse<Map<String, Object>> response = controller.fromRecord(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().get("templateType")).isEqualTo("PDF_FORM");
        assertThat(response.data().get("pdfBase64")).isNotNull();
    }
}
