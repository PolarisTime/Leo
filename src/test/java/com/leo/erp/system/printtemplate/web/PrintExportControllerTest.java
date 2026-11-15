package com.leo.erp.system.printtemplate.web;

import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintRenderOptions;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordOutputResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PrintExportController 资源型打印导出端点测试：
 * 覆盖 PDF 文件表示（200 + application/pdf + Content-Disposition）、脚本资源表示（200 JSON），
 * 以及两类模板走错端点时的 406 契约。
 */
@ExtendWith(MockitoExtension.class)
class PrintExportControllerTest {

    @Mock
    private PrintOutputService printOutputService;

    /** 真实闸门（默认额度 4）：单线程用例直接放行，无需桩化；429 行为由 ExportConcurrencyGuardTest 覆盖。 */
    @org.mockito.Spy
    private com.leo.erp.common.export.ExportConcurrencyGuard exportConcurrencyGuard =
            new com.leo.erp.common.export.ExportConcurrencyGuard(
                    new com.leo.erp.common.export.ExportConcurrencyProperties());

    @InjectMocks
    private PrintExportController controller;

    private Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateName", "销售订单打印");
        payload.put("businessNo", "SO-001");
        payload.put("recordId", 123L);
        payload.put("moduleKey", "sales-order");
        return payload;
    }

    @Test
    void create_shouldReturnPdfFileAsOk() {
        byte[] pdfBytes = {1, 2, 3};
        PrintOutput output = PrintOutput.pdf(
                payload(),
                Base64.getEncoder().encodeToString(pdfBytes),
                MediaType.APPLICATION_PDF_VALUE,
                "SO-001.pdf"
        );
        when(printOutputService.generateFromRecord("tpl-1", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(output);

        ResponseEntity<byte[]> result = controller.createPdf(new PrintRecordRequest("sales-order", "tpl-1", 123L, null));

        // 打印输出是同步命令, 不是创建可回读资源: 200 + 文件表示, 不发 Location
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getLocation()).isNull();
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("SO-001.pdf");
        assertThat(result.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void create_shouldReturnScriptResourceAsOk() {
        Map<String, Object> payload = payload();
        payload.put("templateType", "COORD");
        payload.put("templateHtml", "<div>打印</div>");
        PrintOutput output = PrintOutput.fromPayload(payload);
        when(printOutputService.generateFromRecord("tpl-2", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(output);

        ResponseEntity<PrintRecordOutputResponse> result =
                controller.createScript(new PrintRecordRequest("sales-order", "tpl-2", 123L, null));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        PrintRecordOutputResponse body = result.getBody();
        assertThat(body).isNotNull();
        assertThat(body.kind()).isEqualTo("LODOP_SCRIPT");
        assertThat(body.templateHtml()).isEqualTo("<div>打印</div>");
        // 同步命令无独立可回读资源: 明确断言不发 Location
        assertThat(result.getHeaders().getLocation()).isNull();
    }

    @Test
    void scriptEndpoint_shouldRejectPdfTemplateWith406() {
        byte[] pdfBytes = {1, 2, 3};
        when(printOutputService.generateFromRecord("tpl-1", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(PrintOutput.pdf(
                        payload(),
                        Base64.getEncoder().encodeToString(pdfBytes),
                        MediaType.APPLICATION_PDF_VALUE,
                        "SO-001.pdf"
                ));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        controller.createScript(new PrintRecordRequest("sales-order", "tpl-1", 123L, null)))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .extracting(ex -> ((com.leo.erp.common.error.BusinessException) ex).getErrorCode())
                .isEqualTo(com.leo.erp.common.error.ErrorCode.NOT_ACCEPTABLE);
    }

    @Test
    void pdfEndpoint_shouldRejectScriptTemplateWith406() {
        Map<String, Object> payload = payload();
        payload.put("templateType", "COORD");
        payload.put("templateHtml", "<div>打印</div>");
        when(printOutputService.generateFromRecord("tpl-2", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(PrintOutput.fromPayload(payload));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        controller.createPdf(new PrintRecordRequest("sales-order", "tpl-2", 123L, null)))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .extracting(ex -> ((com.leo.erp.common.error.BusinessException) ex).getErrorCode())
                .isEqualTo(com.leo.erp.common.error.ErrorCode.NOT_ACCEPTABLE);
    }
}
