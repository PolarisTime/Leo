package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintPdfTableRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfFormValueResolver valueResolver = mock(PrintPdfFormValueResolver.class);
    private final PrintPdfDrawingSupport drawing = mock(PrintPdfDrawingSupport.class);
    private final PrintPdfTableRenderer renderer = new PrintPdfTableRenderer(valueResolver, drawing);
    private final PdfCanvas canvas = mock(PdfCanvas.class);
    private final PdfFont font = mock(PdfFont.class);
    private final PdfFont latinFont = mock(PdfFont.class);
    private final PrintPdfDrawingSupport.PageMetrics pageMetrics = new PrintPdfDrawingSupport.PageMetrics(595f, 842f);

    @BeforeEach
    void setUp() {
        when(drawing.number(any(JsonNode.class), anyString(), anyFloat())).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            float fallback = invocation.getArgument(2);
            JsonNode child = node == null ? objectMapper.missingNode() : node.path(field);
            return child.isNumber() ? (float) child.asDouble() : fallback;
        });
        when(drawing.color(any(JsonNode.class), anyString(), nullable(Color.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(drawing.tableWidth(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            return node.path("width").isNumber() ? (float) node.path("width").asDouble() : 120f;
        });
        when(drawing.alignment(anyString())).thenAnswer(invocation -> switch (((String) invocation.getArgument(0)).toLowerCase()) {
            case "right" -> TextAlignment.RIGHT;
            case "center" -> TextAlignment.CENTER;
            default -> TextAlignment.LEFT;
        });
        when(drawing.bool(any(JsonNode.class), anyString(), anyBoolean())).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            boolean fallback = invocation.getArgument(2);
            JsonNode child = node == null ? objectMapper.missingNode() : node.path(field);
            return child.isBoolean() ? child.asBoolean() : fallback;
        });
        when(drawing.isAsciiText(anyString())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value != null && !value.isBlank() && value.chars().allMatch(ch -> ch <= 127);
        });
        when(drawing.childTextValues(any(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode node = invocation.getArgument(0);
            List<String> result = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String value = item.asText("");
                    if (!value.isBlank()) {
                        result.add(value);
                    }
                }
            }
            return result;
        });
    }

    @Test
    void shouldDrawHeaderCellsForEachColumn() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"top\":20,\"headerHeight\":18}");
        List<JsonNode> columns = List.of(
                objectMapper.readTree("{\"label\":\"编号\",\"width\":40}"),
                objectMapper.readTree("{\"label\":\"名称\",\"width\":80}")
        );

        renderer.drawHeader(canvas, font, table, columns, pageMetrics);

        verify(drawing).drawRect(same(canvas), eq(10f), eq(20f), eq(40f), eq(18f),
                nullable(Color.class), nullable(Color.class), anyFloat(), same(pageMetrics));
        verify(drawing).drawRect(same(canvas), eq(50f), eq(20f), eq(80f), eq(18f),
                nullable(Color.class), nullable(Color.class), anyFloat(), same(pageMetrics));
        verify(drawing).drawCanvasText(same(canvas), same(font), eq("编号"),
                eq(12f), eq(26f), eq(36f), eq(16f), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
        verify(drawing).drawCanvasText(same(canvas), same(font), eq("名称"),
                eq(52f), eq(26f), eq(76f), eq(16f), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
    }

    @Test
    void shouldUseLatinFontOnlyForAsciiColumnsConfiguredForLatinFont() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"top\":20,\"rowHeight\":18}");
        JsonNode asciiColumn = objectMapper.readTree("{\"field\":\"code\",\"font\":\"latinIfAscii\",\"width\":40}");
        JsonNode chineseColumn = objectMapper.readTree("{\"field\":\"name\",\"font\":\"latinIfAscii\",\"width\":80}");
        Map<String, String> item = Map.of("code", "ABC", "name", "中文");
        when(valueResolver.itemValue(item, asciiColumn)).thenReturn("ABC");
        when(valueResolver.itemValue(item, chineseColumn)).thenReturn("中文");

        renderer.drawItemRow(canvas, font, latinFont, table, List.of(asciiColumn, chineseColumn), 42f, item, pageMetrics);

        verify(drawing).drawCanvasText(same(canvas), same(latinFont), eq("ABC"),
                anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
        verify(drawing).drawCanvasText(same(canvas), same(font), eq("中文"),
                anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
    }

    @Test
    void shouldDrawNoContentRow() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"width\":160,\"rowHeight\":22,\"emptyText\":\"暂无数据\"}");

        renderer.drawNoContentRow(canvas, font, table, 70f, pageMetrics);

        verify(drawing).drawRect(same(canvas), eq(10f), eq(70f), eq(160f), eq(22f),
                nullable(Color.class), nullable(Color.class), anyFloat(), same(pageMetrics));
        verify(drawing).drawCanvasText(same(canvas), same(font), eq("暂无数据"),
                eq(10f), eq(77f), eq(160f), eq(12f), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
    }

    @Test
    void shouldUseFallbackWhenNoContentTextIsNull() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"width\":160,\"rowHeight\":22,\"emptyText\":null}");

        renderer.drawNoContentRow(canvas, font, table, 70f, pageMetrics);

        verify(drawing).drawCanvasText(same(canvas), same(font), eq(""),
                eq(10f), eq(77f), eq(160f), eq(12f), anyFloat(), eq(TextAlignment.CENTER),
                nullable(Color.class), same(pageMetrics));
    }

    @Test
    void shouldDrawSummaryAndReturnNextTop() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"width\":160,\"rowHeight\":22}");
        JsonNode summary = objectMapper.readTree("{\"height\":30,\"template\":\"合计 ${total}\",\"align\":\"right\"}");
        when(valueResolver.applyTemplate("合计 ${total}", Map.of("total", "100"))).thenReturn("合计 100");

        float nextTop = renderer.drawSummary(canvas, font, summary, table, Map.of("total", "100"), 80f, pageMetrics);

        assertThat(nextTop).isEqualTo(110f);
        verify(drawing).drawRect(same(canvas), eq(10f), eq(80f), eq(160f), eq(30f),
                nullable(Color.class), nullable(Color.class), anyFloat(), same(pageMetrics));
        verify(drawing).drawCanvasText(same(canvas), same(font), eq("合计 100"),
                anyFloat(), anyFloat(), anyFloat(), eq(12f), anyFloat(), eq(TextAlignment.RIGHT),
                nullable(Color.class), same(pageMetrics));
    }

    @Test
    void shouldReturnOriginalTopForNonObjectSummaryAndSkipBorderWhenDisabled() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"width\":160,\"rowHeight\":22}");
        JsonNode summary = objectMapper.readTree("{\"height\":30,\"border\":false,\"template\":\"合计\"}");

        assertThat(renderer.drawSummary(canvas, font, objectMapper.readTree("[]"), table, Map.of(), 80f, pageMetrics))
                .isEqualTo(80f);
        assertThat(renderer.drawSummary(canvas, font, summary, table, Map.of(), 90f, pageMetrics))
                .isEqualTo(120f);

        verify(drawing, never()).drawRect(same(canvas), eq(10f), eq(90f), eq(160f), eq(30f),
                nullable(Color.class), nullable(Color.class), anyFloat(), same(pageMetrics));
    }

    @Test
    void shouldDrawClausesOnlyWhenLinesArePresent() throws Exception {
        JsonNode table = objectMapper.readTree("{\"left\":10,\"width\":160}");
        JsonNode emptyClauses = objectMapper.readTree("{\"lines\":[]}");
        JsonNode clauses = objectMapper.readTree("{\"lines\":[\"第一条\",\" \",\"第二条\"],\"left\":12,\"width\":150}");

        renderer.drawClauses(canvas, font, objectMapper.readTree("[]"), table, 100f, pageMetrics);
        renderer.drawClauses(canvas, font, emptyClauses, table, 100f, pageMetrics);
        renderer.drawClauses(canvas, font, clauses, table, 120f, pageMetrics);

        verify(drawing).drawParagraphs(same(canvas), same(font), eq(List.of("第一条", "第二条")),
                eq(12f), eq(128f), eq(150f), anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                nullable(Color.class), same(pageMetrics));
    }
}
