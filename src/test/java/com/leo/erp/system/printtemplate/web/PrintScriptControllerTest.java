package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintRecordItem;
import com.leo.erp.system.printtemplate.service.PrintRenderOptions;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintScriptControllerTest {

    private final PrintScriptService printScriptService = mock(PrintScriptService.class);
    private final PrintOutputService printOutputService = mock(PrintOutputService.class);
    private final ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
    private final PrintScriptController controller = new PrintScriptController(printScriptService, printOutputService, modulePermissionGuard);

    @Test
    void fromRecordReturnsGeneratedResult() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        PrintRecordRequest payload = new PrintRecordRequest("sales-order", "template-1", 1L, null);

        PrintOutput result = new PrintOutput(
                PrintOutput.Kind.LODOP_SCRIPT,
                "Test Template",
                "COORD",
                null,
                null,
                null,
                "SO-001",
                1L,
                "sales-order",
                "LODOP.PRINT_INIT(\"test\");",
                Map.of("name", "test"),
                List.of()
        );

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printOutputService.generateFromRecord(eq("template-1"), eq("sales-order"), eq(1L), any(PrintRenderOptions.class))).thenReturn(result);

        ApiResponse<PrintOutput> response = controller.fromRecord(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(result);
        verify(printOutputService).generateFromRecord("template-1", "sales-order", 1L, PrintRenderOptions.defaults());
    }

    @Test
    void fromRecordPassesPrintOptions() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        PrintRecordRequest payload = new PrintRecordRequest(
                "sales-order",
                "template-1",
                1L,
                new PrintRenderOptions(
                        true,
                        true,
                        "",
                        Map.of("抚顺新钢", " 抚新 "),
                        Map.of("11", " 沙钢 "),
                        List.of("12", "11")
                )
        );

        PrintOutput result = new PrintOutput(
                PrintOutput.Kind.LODOP_SCRIPT,
                "Test Template",
                "COORD",
                null,
                null,
                null,
                "SO-001",
                1L,
                "sales-order",
                "LODOP.PRINT_INIT(\"test\");",
                Map.of(),
                List.of()
        );

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printOutputService.generateFromRecord(
                eq("template-1"),
                eq("sales-order"),
                eq(1L),
                eq(new PrintRenderOptions(true, true, "", Map.of("抚顺新钢", "抚新"), Map.of("11", "沙钢"), List.of("12", "11")))
        )).thenReturn(result);

        ApiResponse<PrintOutput> response = controller.fromRecord(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        verify(printOutputService).generateFromRecord(
                "template-1",
                "sales-order",
                1L,
                new PrintRenderOptions(true, true, "", Map.of("抚顺新钢", "抚新"), Map.of("11", "沙钢"), List.of("12", "11"))
        );
    }

    @Test
    void brandsReturnsRecordBrands() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("recordIds", List.of("1", 2L));

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.listBrands("sales-order", List.of(1L, 2L))).thenReturn(List.of("抚顺新钢", "沙钢"));

        ApiResponse<List<String>> response = controller.brands(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly("抚顺新钢", "沙钢");
    }

    @Test
    void itemsReturnsRecordItems() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("recordIds", List.of("1"));
        List<PrintRecordItem> items = List.of(new PrintRecordItem(
                "11", "1", "中杭", "螺纹钢", "HRB400E", "Ф18", "12", "0.123", "1.476", "3300.00", "4870.80"
        ));

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.listPrintItems("sales-order", List.of(1L))).thenReturn(items);

        ApiResponse<List<PrintRecordItem>> response = controller.items(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(items);
    }

    @Test
    void itemsUsesEmptyRecordIdsForInvalidPayload() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("recordIds", "1");

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.listPrintItems("sales-order", List.of())).thenReturn(List.of());

        ApiResponse<List<PrintRecordItem>> response = controller.items(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEmpty();
        verify(printScriptService).listPrintItems("sales-order", List.of());
    }

    @Test
    void itemsIgnoresInvalidRecordIdValues() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "sales-order");
        payload.put("recordIds", List.of("1", "abc", " 2 ", ""));

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printScriptService.listPrintItems("sales-order", List.of(1L, 2L))).thenReturn(List.of());

        ApiResponse<List<PrintRecordItem>> response = controller.items(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEmpty();
        verify(printScriptService).listPrintItems("sales-order", List.of(1L, 2L));
    }

    @Test
    void fromRecordWithPdfFormReturnsPdfBase64() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        PrintRecordRequest payload = new PrintRecordRequest("sales-order", "template-1", 1L, null);

        PrintOutput result = new PrintOutput(
                PrintOutput.Kind.PDF,
                "Test Template",
                "PDF_FORM",
                "application/pdf",
                "print.pdf",
                "cGRmLWNvbnRlbnQ=",
                "SO-001",
                1L,
                "sales-order",
                null,
                null,
                null
        );

        when(modulePermissionGuard.requirePermission(principal, "sales-order", "read")).thenReturn("sales-order");
        when(printOutputService.generateFromRecord(eq("template-1"), eq("sales-order"), eq(1L), any(PrintRenderOptions.class))).thenReturn(result);

        ApiResponse<PrintOutput> response = controller.fromRecord(principal, payload);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().templateType()).isEqualTo("PDF_FORM");
        assertThat(response.data().pdfBase64()).isNotNull();
    }

    @Test
    void fromRecordHasOperationLogAnnotation() throws Exception {
        OperationLoggable annotation = PrintScriptController.class
                .getMethod("fromRecord", SecurityPrincipal.class, PrintRecordRequest.class)
                .getAnnotation(OperationLoggable.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.actionType()).isEqualTo("打印");
        assertThat(annotation.moduleNameField()).isEqualTo("moduleKey");
        assertThat(annotation.businessNoFields()).containsExactly("businessNo");
        assertThat(annotation.recordIdField()).isEqualTo("recordId");
        assertThat(annotation.moduleKeyField()).isEqualTo("moduleKey");
    }
}
