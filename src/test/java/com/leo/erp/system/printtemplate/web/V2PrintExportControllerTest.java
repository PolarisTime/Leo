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
 * V2PrintExportController 资源型打印导出端点测试：
 * 覆盖 PDF 同步文件响应（201 + Content-Type/Content-Disposition）与脚本资源表示（201 JSON）。
 */
@ExtendWith(MockitoExtension.class)
class V2PrintExportControllerTest {

    @Mock
    private PrintOutputService printOutputService;

    @InjectMocks
    private V2PrintExportController controller;

    private Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateName", "销售订单打印");
        payload.put("businessNo", "SO-001");
        payload.put("recordId", 123L);
        payload.put("moduleKey", "sales-order");
        return payload;
    }

    @Test
    void create_shouldReturnPdfFileAsCreated() {
        byte[] pdfBytes = {1, 2, 3};
        PrintOutput output = PrintOutput.pdf(
                payload(),
                Base64.getEncoder().encodeToString(pdfBytes),
                MediaType.APPLICATION_PDF_VALUE,
                "SO-001.pdf"
        );
        when(printOutputService.generateFromRecord("tpl-1", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(output);

        ResponseEntity<?> result = controller.create(new PrintRecordRequest("sales-order", "tpl-1", 123L, null));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("SO-001.pdf");
        assertThat((byte[]) result.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void create_shouldReturnScriptResourceAsCreated() {
        Map<String, Object> payload = payload();
        payload.put("templateType", "COORD");
        payload.put("templateHtml", "<div>打印</div>");
        PrintOutput output = PrintOutput.fromPayload(payload);
        when(printOutputService.generateFromRecord("tpl-2", "sales-order", 123L, PrintRenderOptions.defaults()))
                .thenReturn(output);

        ResponseEntity<?> result = controller.create(new PrintRecordRequest("sales-order", "tpl-2", 123L, null));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isInstanceOf(PrintRecordOutputResponse.class);
        PrintRecordOutputResponse body = (PrintRecordOutputResponse) result.getBody();
        assertThat(body.kind()).isEqualTo("LODOP_SCRIPT");
        assertThat(body.templateHtml()).isEqualTo("<div>打印</div>");
        assertThat(result.getHeaders().getLocation()).isNull();
    }
}
