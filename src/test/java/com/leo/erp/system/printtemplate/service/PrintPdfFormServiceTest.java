package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PrintPdfFormServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateFromPayloadShouldThrowWhenTemplateTypeNotPdfForm() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of("templateType", "HTML");

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void generateFromPayloadShouldThrowWhenTemplateConfigInvalid() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "not-valid-json",
                "data", Collections.emptyMap(),
                "items", Collections.emptyList()
        );

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void generateFromPayloadShouldGeneratePdfWithoutBaseTemplate() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", """
                        {
                          "page": {"width": 595, "height": 842},
                          "static": [{"type": "text", "text": "测试标题", "left": 20, "top": 20, "width": 200, "height": 20, "fontSize": 12}],
                          "fields": {"billNo": {"source": "billNo", "left": 20, "top": 50, "width": 160, "height": 18, "fontSize": 10}},
                          "table": {
                            "left": 20,
                            "top": 80,
                            "rowHeight": 20,
                            "headerHeight": 20,
                            "maxRowsPerPage": 1,
                            "columns": [{"key": "material", "label": "材质", "width": 80}]
                          }
                        }
                        """,
                "data", Map.of("billNo", "SO-001"),
                "items", List.of(Map.of("material", "HRB400E"), Map.of("material", "HRB500E"))
        );

        byte[] pdf = service.generateFromPayload(payload);

        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void generateFromPayloadShouldRejectLegacyFormConfig() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "{\"form\":\"YINGJIE_A4_REMARK\",\"template\":\"print-forms/yingjie-a4-remark.pdf\"}",
                "data", Collections.emptyMap(),
                "items", Collections.emptyList()
        );

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 form 专用配置");
    }

    @Test
    void generateFromPayloadShouldRejectTrailingJsonTokens() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "{\"static\":[]} {}",
                "data", Collections.emptyMap(),
                "items", Collections.emptyList()
        );

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    private PrintPdfFormService service(PrintScriptService printScriptService) {
        return new PrintPdfFormService(
                printScriptService,
                new PrintPdfFormTemplateValidator(objectMapper)
        );
    }
}
