package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintOutputServiceTest {

    private final PrintScriptService printScriptService = mock(PrintScriptService.class);
    private final PrintPdfFormService printPdfFormService = mock(PrintPdfFormService.class);
    private final PrintOutputService service = new PrintOutputService(printScriptService, printPdfFormService);

    @Test
    void shouldReturnLodopScriptOutputForCoordPayload() {
        Map<String, Object> payload = Map.of(
                "templateName", "坐标模板",
                "templateType", "COORD",
                "businessNo", "SO-001",
                "recordId", 1L,
                "moduleKey", "sales-order",
                "templateHtml", "LODOP.PRINT_INIT(\"test\");",
                "data", Map.of("customerName", "客户甲"),
                "items", List.of(Map.of("brand", "中杭"))
        );
        when(printScriptService.generateFromRecord("template-1", "sales-order", 1L, PrintRenderOptions.defaults()))
                .thenReturn(payload);

        PrintOutput output = service.generateFromRecord("template-1", "sales-order", 1L, PrintRenderOptions.defaults());

        assertThat(output.kind()).isEqualTo(PrintOutput.Kind.LODOP_SCRIPT);
        assertThat(output.templateType()).isEqualTo("COORD");
        assertThat(output.businessNo()).isEqualTo("SO-001");
        assertThat(output.recordId()).isEqualTo(1L);
        assertThat(output.moduleKey()).isEqualTo("sales-order");
        assertThat(output.templateHtml()).isEqualTo("LODOP.PRINT_INIT(\"test\");");
        assertThat(output.data()).containsEntry("customerName", "客户甲");
        assertThat(output.items()).hasSize(1);
    }

    @Test
    void shouldRenderPdfOutputForPdfFormPayload() {
        Map<String, Object> payload = Map.of(
                "templateName", "PDF 模板",
                "templateType", "PDF_FORM",
                "businessNo", "SO-001",
                "recordId", 1L,
                "moduleKey", "sales-order"
        );
        when(printScriptService.generateFromRecord("template-1", "sales-order", 1L, PrintRenderOptions.defaults()))
                .thenReturn(payload);
        when(printPdfFormService.generateFromPayload(payload)).thenReturn("pdf-content".getBytes());

        PrintOutput output = service.generateFromRecord("template-1", "sales-order", 1L, PrintRenderOptions.defaults());

        assertThat(output.kind()).isEqualTo(PrintOutput.Kind.PDF);
        assertThat(output.templateType()).isEqualTo("PDF_FORM");
        assertThat(output.contentType()).isEqualTo("application/pdf");
        assertThat(output.fileName()).isEqualTo("print.pdf");
        assertThat(output.pdfBase64()).isEqualTo("cGRmLWNvbnRlbnQ=");
        verify(printPdfFormService).generateFromPayload(payload);
    }
}
