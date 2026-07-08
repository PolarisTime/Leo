package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintPdfFormRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfFormValueResolver valueResolver = mock(PrintPdfFormValueResolver.class);
    private final PrintPdfFontFactory fontFactory = mock(PrintPdfFontFactory.class);
    private final PrintPdfDrawingSupport drawing = mock(PrintPdfDrawingSupport.class);
    private final PrintPdfPageContentRenderer pageContentRenderer = mock(PrintPdfPageContentRenderer.class);
    private final PrintPdfTableRenderer tableRenderer = mock(PrintPdfTableRenderer.class);
    private PrintPdfFormRenderer renderer;
    private PdfFont font;

    @BeforeEach
    void setUp() throws Exception {
        font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        when(fontFactory.createChineseFont()).thenReturn(font);
        when(fontFactory.createLatinFont(font)).thenReturn(font);
        when(valueResolver.summaryVariables(any(), any())).thenReturn(Map.of());
        when(drawing.pageMetrics(any())).thenReturn(new PrintPdfDrawingSupport.PageMetrics(595f, 842f));
        when(drawing.number(any(), eq("top"), anyFloat())).thenReturn(176f);
        when(drawing.number(any(), eq("rowHeight"), anyFloat())).thenReturn(26f);
        when(drawing.number(any(), eq("headerHeight"), anyFloat())).thenReturn(28f);
        when(drawing.integer(any(), eq("maxRowsPerPage"), eq(16))).thenReturn(16);
        when(drawing.childObjects(any())).thenReturn(List.of());
        when(tableRenderer.drawSummary(any(), any(), any(), any(), any(), anyFloat(), any()))
                .thenAnswer(invocation -> invocation.getArgument(5));
        renderer = new PrintPdfFormRenderer(valueResolver, fontFactory, drawing, pageContentRenderer, tableRenderer);
    }

    @Test
    void renderShouldWrapIOExceptionAsBusinessException() throws Exception {
        when(fontFactory.createChineseFont()).thenThrow(new IOException("font missing"));
        PrintPdfFormPayload payload = payload("{}");

        assertThatThrownBy(() -> renderer.render(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成 PDF 表单失败");
    }

    @Test
    void renderShouldSkipDrawingWhenTemplateHasNoRenderableSections() throws Exception {
        byte[] result = renderer.render(payload("{}"));

        assertThat(result).isNotEmpty();
        verify(pageContentRenderer, never()).drawStatic(any(), any(), any(), any(), any());
        verify(pageContentRenderer, never()).drawFields(any(), any(), any(), any(), any());
        verify(tableRenderer, never()).drawHeader(any(), any(), any(), any(), any());
    }

    @Test
    void renderShouldDrawStaticOnlyTemplate() throws Exception {
        PrintPdfFormPayload payload = payload("{\"static\":[]}");

        byte[] result = renderer.render(payload);

        assertThat(result).isNotEmpty();
        verify(pageContentRenderer).drawStatic(any(), same(font), any(), eq(Map.of()), any());
        verify(pageContentRenderer).drawFields(any(), any(), any(), same(font), any());
        verify(tableRenderer, never()).drawHeader(any(), any(), any(), any(), any());
    }

    @Test
    void renderShouldDrawFieldsOnlyTemplate() throws Exception {
        PrintPdfFormPayload payload = payload("{\"fields\":{}}");

        byte[] result = renderer.render(payload);

        assertThat(result).isNotEmpty();
        verify(pageContentRenderer).drawStatic(any(), same(font), any(), eq(Map.of()), any());
        verify(pageContentRenderer).drawFields(any(), any(), any(), same(font), any());
        verify(tableRenderer, never()).drawHeader(any(), any(), any(), any(), any());
    }

    @Test
    void renderShouldDrawNoContentRowWhenTableHasNoItems() throws Exception {
        PrintPdfFormPayload payload = payload("""
                {
                  "table": {
                    "top": 120,
                    "rowHeight": 20,
                    "headerHeight": 24,
                    "columns": []
                  }
                }
                """);

        byte[] result = renderer.render(payload);

        assertThat(result).isNotEmpty();
        verify(tableRenderer).drawHeader(any(), same(font), any(), eq(List.of()), any());
        verify(tableRenderer).drawNoContentRow(any(), same(font), any(), eq(204f), any());
    }

    @Test
    void renderShouldDrawTablesFromNamedSections() throws Exception {
        PrintPdfFormPayload payload = new PrintPdfFormPayload(
                objectMapper.readTree("""
                        {
                          "tables": [
                            {"source": "items", "columns": []},
                            {"source": "chargeItems", "columns": []}
                          ]
                        }
                        """),
                Map.of("businessNo", "PO-001"),
                List.of(Map.of("material", "HRB400E")),
                List.of(Map.of("chargeName", "卸货费", "amount", "12.50")),
                Map.of(
                        "items", List.of(Map.of("material", "HRB400E")),
                        "chargeItems", List.of(Map.of("chargeName", "卸货费", "amount", "12.50"))
                )
        );

        byte[] result = renderer.render(payload);

        assertThat(result).isNotEmpty();
        verify(tableRenderer, times(2)).drawHeader(any(), same(font), any(), eq(List.of()), any());
        verify(tableRenderer).drawItemRow(
                any(),
                same(font),
                same(font),
                any(),
                eq(List.of()),
                anyFloat(),
                argThat(item -> "HRB400E".equals(item.get("material"))),
                any()
        );
        verify(tableRenderer).drawItemRow(
                any(),
                same(font),
                same(font),
                any(),
                eq(List.of()),
                anyFloat(),
                argThat(item -> "卸货费".equals(item.get("chargeName"))),
                any()
        );
    }

    @Test
    void renderShouldIncludeChargeTableTextFromDefaultLayout() throws Exception {
        PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
        PrintPdfFormValueResolver actualValueResolver = new PrintPdfFormValueResolver(runtimeProperties);
        PrintPdfDrawingSupport actualDrawing = new PrintPdfDrawingSupport();
        PrintPdfFormRenderer actualRenderer = new PrintPdfFormRenderer(
                actualValueResolver,
                new PrintPdfFontFactory(),
                actualDrawing,
                new PrintPdfPageContentRenderer(actualValueResolver, actualDrawing),
                new PrintPdfTableRenderer(actualValueResolver, actualDrawing)
        );
        List<Map<String, String>> items = List.of(Map.ofEntries(
                Map.entry("index", "1"),
                Map.entry("materialCode", "M-001"),
                Map.entry("brand", "沙钢"),
                Map.entry("category", "螺纹钢"),
                Map.entry("material", "HRB400E"),
                Map.entry("spec", "18"),
                Map.entry("length", "9"),
                Map.entry("quantity", "2"),
                Map.entry("pieceWeightTon", "1.000"),
                Map.entry("weightTon", "2.000"),
                Map.entry("unitPrice", "50.00"),
                Map.entry("amount", "100.00")
        ));
        List<Map<String, String>> chargeItems = List.of(Map.ofEntries(
                Map.entry("lineNo", "1"),
                Map.entry("chargeName", "卸货费"),
                Map.entry("chargeDirection", "PAYABLE"),
                Map.entry("settlementPartyName", "供应商A"),
                Map.entry("amount", "12.34"),
                Map.entry("billable", "true")
        ));
        String template = new ClassPathResource("print-forms/default-purchase-order.layout.json")
                .getContentAsString(StandardCharsets.UTF_8);
        PrintPdfFormPayload payload = new PrintPdfFormPayload(
                objectMapper.readTree(template),
                Map.of(
                        "moduleKey", "purchase-order",
                        "orderNo", "PO-001",
                        "supplierName", "供应商A",
                        "totalAmount", "100.00",
                        "remark", "无"
                ),
                items,
                chargeItems,
                Map.of(
                        "items", items,
                        "chargeItems", chargeItems
                )
        );

        byte[] result = actualRenderer.render(payload);

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(result)))) {
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text).contains("其他费用", "费用名称", "金额", "卸货费", "12.34");
        }
    }

    private PrintPdfFormPayload payload(String json) throws Exception {
        return new PrintPdfFormPayload(
                objectMapper.readTree(json),
                Map.of("businessNo", "SO-001"),
                List.of(),
                List.of(),
                Map.of("items", List.of(), "chargeItems", List.of())
        );
    }
}
