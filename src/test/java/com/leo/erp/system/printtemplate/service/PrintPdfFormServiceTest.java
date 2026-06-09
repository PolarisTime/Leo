package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    void generateFromPayloadShouldRenderAntdStyleFields() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", """
                        {
                          "page": {"width": 595, "height": 842},
                          "static": [
                            {"type": "rect", "left": 20, "top": 20, "width": 180, "height": 32, "fillColor": "#fafafa", "strokeColor": "#d9d9d9", "lineWidth": 0.8},
                            {"type": "line", "x1": 20, "y1": 60, "x2": 200, "y2": 60, "strokeColor": "#1677ff", "lineWidth": 1.2},
                            {"type": "text", "text": "AntD 样式", "left": 26, "top": 27, "width": 160, "height": 16, "fontSize": 12, "color": "#1677ff"}
                          ],
                          "fields": {"billNo": {"source": "billNo", "left": 20, "top": 70, "width": 160, "height": 18, "fontSize": 10, "color": "#1f1f1f"}},
                          "table": {
                            "left": 20,
                            "top": 100,
                            "rowHeight": 20,
                            "headerHeight": 20,
                            "maxRowsPerPage": 2,
                            "headerFillColor": "#f5f7fa",
                            "borderColor": "#d9d9d9",
                            "headerTextColor": "#1677ff",
                            "textColor": "#1f1f1f",
                            "emptyFillColor": "#fafafa",
                            "emptyTextColor": "#8c8c8c",
                            "lineWidth": 0.8,
                            "columns": [{"key": "material", "source": ["material", "materialCode"], "label": "材质", "width": 80, "textColor": "#1f1f1f"}]
                          },
                          "summary": {"height": 20, "template": "备注：${remark}", "fillColor": "#e6f4ff", "borderColor": "#d9d9d9", "textColor": "#1677ff"},
                          "clauses": {"height": 32, "fontSize": 8, "textColor": "#595959", "lines": ["1. 条款"]}
                        }
                        """,
                "data", Map.of("billNo", "SO-001", "remark", "无"),
                "items", List.of(Map.of("materialCode", "HRB400E"))
        );

        byte[] pdf = service.generateFromPayload(payload);

        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void defaultJsonLayoutsShouldGeneratePdf() throws IOException {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = service(printScriptService);

        for (String resource : List.of(
                "print-forms/default-purchase.layout.json",
                "print-forms/default-sales.layout.json",
                "print-forms/default-logistics.layout.json",
                "print-forms/default-statement.layout.json",
                "print-forms/default-report.layout.json"
        )) {
            String template = new org.springframework.core.io.ClassPathResource(resource)
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> payload = Map.of(
                    "templateType", "PDF_FORM",
                    "templateHtml", template,
                    "data", Map.of(
                            "billNo", "B-001",
                            "orderNo", "SO-001",
                            "customerName", "测试客户",
                            "supplierName", "测试供应商",
                            "projectName", "测试项目",
                            "remark", "无"
                    ),
                    "items", List.of(Map.ofEntries(
                            Map.entry("materialCode", "M-001"),
                            Map.entry("brand", "中天"),
                            Map.entry("category", "直条"),
                            Map.entry("material", "HRB400E"),
                            Map.entry("spec", "16"),
                            Map.entry("length", "9"),
                            Map.entry("quantity", "2"),
                            Map.entry("pieceWeightTon", "1.234"),
                            Map.entry("weightTon", "2.468"),
                            Map.entry("unitPrice", "3600"),
                            Map.entry("amount", "8884.80")
                    ))
            );

            byte[] pdf = service.generateFromPayload(payload);

            assertThat(pdf)
                    .as(resource)
                    .startsWith("%PDF".getBytes());
        }
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
