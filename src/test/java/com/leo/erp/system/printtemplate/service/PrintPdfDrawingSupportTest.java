package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.properties.TextAlignment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        assertThat(drawing.alignment(" center ")).isEqualTo(TextAlignment.CENTER);
        assertThat(drawing.alignment(null)).isEqualTo(TextAlignment.LEFT);
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
        assertThat(drawing.childTextValues(root.path("columns").get(0))).isEmpty();
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
        assertThat(drawing.fitFontSize(font, "ABCDEFGHIJ", 0f, 12f, 6f)).isEqualTo(12f);
        assertThat(drawing.wrapLines(font, "\nA", 12f, 20f)).containsExactly("A");
        assertThat(drawing.truncateToWidth(font, "ABC", 12f, 200f)).isEqualTo("ABC");
        assertThat(lines).hasSizeGreaterThan(1);
        assertThat(truncated).endsWith("...");
        assertThat(drawing.truncateToWidth(font, "ABC", 12f, 1f)).isEqualTo("...");
    }

    @Test
    void shouldResolveRectanglesAndPageMetrics() throws Exception {
        var pageMetrics = drawing.pageMetrics(objectMapper.readTree("{\"width\":600,\"height\":900}"));
        var rectangle = drawing.configuredRectangle(
                objectMapper.readTree("{\"left\":10,\"top\":20,\"width\":30,\"height\":40}"),
                pageMetrics
        );

        assertThat(pageMetrics.width()).isEqualTo(600f);
        assertThat(pageMetrics.height()).isEqualTo(900f);
        assertThat(rectangle.getX()).isEqualTo(10f);
        assertThat(rectangle.getY()).isEqualTo(840f);
        assertThat(rectangle.getWidth()).isEqualTo(30f);
        assertThat(rectangle.getHeight()).isEqualTo(40f);
        assertThat(drawing.configuredRectangle(objectMapper.readTree("{\"left\":10}"), pageMetrics)).isNull();
        assertThat(drawing.configuredRectangle(objectMapper.readTree("{\"left\":10,\"top\":20}"), pageMetrics)).isNull();
        assertThat(drawing.configuredRectangle(objectMapper.readTree("{\"left\":10,\"top\":20,\"width\":30}"), pageMetrics)).isNull();
        assertThat(drawing.configuredRectangle(objectMapper.readTree("{\"left\":10,\"top\":20,\"height\":40}"), pageMetrics)).isNull();
        assertThat(drawing.configuredRectangle(objectMapper.readTree("{\"top\":20,\"width\":30,\"height\":40}"), pageMetrics)).isNull();
        assertThat(drawing.pageMetrics(objectMapper.readTree("{}")).height()).isEqualTo(842f);
    }

    @Test
    void shouldParseFallbackValuesAndAsciiText() throws Exception {
        var node = objectMapper.readTree("""
                {
                  "badColor": "#xyzxyz",
                  "shortColor": "#fff",
                  "plainColor": "1677ff",
                  "nullText": null,
                  "numberAsString": "3"
                }
                """);

        assertThat(drawing.text(node, "nullText", "fallback")).isEqualTo("fallback");
        assertThat(drawing.integer(node, "numberAsString", 7)).isEqualTo(7);
        assertThat(drawing.color(node, "badColor", ColorConstants.RED)).isSameAs(ColorConstants.RED);
        assertThat(drawing.color(node, "shortColor", ColorConstants.BLUE)).isSameAs(ColorConstants.BLUE);
        assertThat(drawing.color(node, "plainColor", null)).isInstanceOf(DeviceRgb.class);
        assertThat(drawing.isAsciiText(null)).isFalse();
        assertThat(drawing.isAsciiText("")).isFalse();
        assertThat(drawing.isAsciiText("ABC-123")).isTrue();
        assertThat(drawing.isAsciiText("钢材")).isFalse();
        assertThat(drawing.topY(42f, new PrintPdfDrawingSupport.PageMetrics(100f, 900f))).isEqualTo(858f);
    }

    @Test
    void shouldDrawPrimitiveShapesAndCanvasText() throws Exception {
        byte[] bytes = writePdf(canvas -> {
            var metrics = new PrintPdfDrawingSupport.PageMetrics(595f, 842f);
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            drawing.drawRect(
                    canvas,
                    10f,
                    20f,
                    60f,
                    30f,
                    objectMapper.readTree("{\"fillColor\":\"#1677ff\",\"strokeColor\":\"#ff0000\",\"lineWidth\":0}")
                    ,
                    metrics
            );
            drawing.drawRect(canvas, 80f, 20f, 60f, 30f, null, ColorConstants.BLACK, 1f, metrics);
            drawing.drawLine(canvas, objectMapper.readTree("{\"x1\":10,\"y1\":70,\"x2\":120,\"y2\":70}"), metrics);
            drawing.drawCanvasText(canvas, font, null, 10f, 100f, 80f, 20f, 12f, TextAlignment.CENTER, ColorConstants.BLACK, metrics);
            drawing.drawCanvasText(canvas, font, "Right", 10f, 130f, 80f, 20f, 12f, TextAlignment.RIGHT, ColorConstants.BLACK, metrics);
            drawing.drawCanvasText(canvas, font, "Left", 10f, 160f, 80f, 20f, 12f, TextAlignment.LEFT, ColorConstants.BLACK, metrics);
        });

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void shouldDrawSingleLineAndWrappedText() throws Exception {
        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 700f, 120f, 30f),
                    "single",
                    objectMapper.readTree("{\"align\":\"right\",\"vertical\":\"top\",\"color\":\"#333333\"}"),
                    "Single line",
                    font
            );
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 620f, 40f, 40f),
                    "wrapped",
                    objectMapper.readTree("{\"multiline\":true,\"align\":\"center\",\"vertical\":\"middle\",\"lineHeight\":1.1,\"minimumFontSize\":6}"),
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                    font
            );
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 600f, 40f, 10f),
                    "emptyWrapped",
                    objectMapper.readTree("{\"multiline\":true,\"minimumFontSize\":6}"),
                    "",
                    font
            );
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 560f, 180f, 30f),
                    "oneLineMultiline",
                    objectMapper.readTree("{\"multiline\":true,\"vertical\":\"middle\"}"),
                    "Short",
                    font
            );
        });

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void shouldReturnWhenWrappedTextProducesNoLines() throws Exception {
        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 600f, 40f, 20f),
                    "blankWrapped",
                    objectMapper.readTree("{\"multiline\":true,\"minimumFontSize\":6}"),
                    "\n",
                    font
            );
        });

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void shouldReturnWithoutDrawingWhenWrappedTextHasNoVisibleLines() throws Exception {
        PdfCanvas canvas = mock(PdfCanvas.class);
        var font = mock(com.itextpdf.kernel.font.PdfFont.class);
        when(font.getWidth("\n", 9f)).thenReturn(2f);

        drawing.drawText(
                canvas,
                new Rectangle(20f, 600f, 2f, 20f),
                "blankWrapped",
                objectMapper.readTree("{\"multiline\":true}"),
                "\n",
                font
        );

        verifyNoInteractions(canvas);
    }

    @Test
    void shouldAllowTruncateLoopToConsumeAllCharactersWhenFontWidthAllowsSuffix() {
        var font = mock(com.itextpdf.kernel.font.PdfFont.class);
        when(font.getWidth("AB", 12f)).thenReturn(20f);
        when(font.getWidth("A...", 12f)).thenReturn(1f);
        when(font.getWidth("AB...", 12f)).thenReturn(1f);

        assertThat(drawing.truncateToWidth(font, "AB", 12f, 10f)).isEqualTo("AB...");
    }

    @Test
    void shouldUseMinimumFontSizeWhenMultilineTextStillExceedsMaxLines() throws Exception {
        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            drawing.drawText(
                    canvas,
                    new Rectangle(20f, 600f, 8f, 8f),
                    "tinyWrapped",
                    objectMapper.readTree("{\"multiline\":true,\"fontSize\":12,\"minimumFontSize\":11,\"lineHeight\":1.2}"),
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                    font
            );
        });

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void shouldDrawParagraphsAndLimitLines() throws Exception {
        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            var metrics = new PrintPdfDrawingSupport.PageMetrics(595f, 842f);
            drawing.drawParagraphs(
                    canvas,
                    font,
                    List.of("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789"),
                    20f,
                    300f,
                    32f,
                    18f,
                    12f,
                    6f,
                    1.2f,
                    ColorConstants.BLACK,
                    metrics
            );
            drawing.drawParagraphs(
                    canvas,
                    font,
                    List.of("A"),
                    20f,
                    340f,
                    120f,
                    24f,
                    10f,
                    6f,
                    1.2f,
                    ColorConstants.BLACK,
                    metrics
            );
        });

        assertThat(bytes).isNotEmpty();
    }

    private byte[] writePdf(PdfCanvasConsumer consumer) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(output);
             PdfDocument document = new PdfDocument(writer)) {
            var page = document.addNewPage(PageSize.A4);
            consumer.accept(new PdfCanvas(page));
            document.close();
            return output.toByteArray();
        }
    }

    @FunctionalInterface
    private interface PdfCanvasConsumer {
        void accept(PdfCanvas canvas) throws Exception;
    }
}
