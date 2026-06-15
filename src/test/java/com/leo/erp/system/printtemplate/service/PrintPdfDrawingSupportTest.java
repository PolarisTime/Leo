package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfDrawingSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfDrawingSupport drawing = new PrintPdfDrawingSupport();

    @Test
    void shouldReadLayoutConfigWithFallbacks() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "width": 120.5,
                  "rows": 3,
                  "enabled": true,
                  "align": "right",
                  "color": "#1677ff"
                }
                """);

        assertThat(drawing.number(node, "width", 10f)).isEqualTo(120.5f);
        assertThat(drawing.number(node, "missing", 10f)).isEqualTo(10f);
        assertThat(drawing.integer(node, "rows", 1)).isEqualTo(3);
        assertThat(drawing.bool(node, "enabled", false)).isTrue();
        assertThat(drawing.alignment("right")).isEqualTo(TextAlignment.RIGHT);
        assertThat(drawing.color(node, "color", null)).isInstanceOf(DeviceRgb.class);
    }

    @Test
    void shouldCollectOnlySupportedChildValues() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "columns": [{"key": "a"}, "skip", {"key": "b"}],
                  "lines": ["第一行", "", "第二行"]
                }
                """);

        assertThat(drawing.childObjects(root.path("columns"))).hasSize(2);
        assertThat(drawing.childTextValues(root.path("lines"))).containsExactly("第一行", "第二行");
        assertThat(drawing.tableWidth(objectMapper.readTree("""
                {"columns": [{"width": 50}, {"width": 70}]}
                """))).isEqualTo(120f);
    }

    @Test
    void shouldFitAndWrapTextByFontWidth() throws Exception {
        var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        float fitted = drawing.fitFontSize(font, "ABCDEFGHIJ", 20f, 12f, 6f);
        List<String> lines = drawing.wrapLines(font, "ABCDE", 12f, 20f);
        String truncated = drawing.truncateToWidth(font, "ABCDEFGHIJ", 12f, 28f);

        assertThat(fitted).isBetween(6f, 12f);
        assertThat(lines).hasSizeGreaterThan(1);
        assertThat(truncated).endsWith("...");
    }
}
