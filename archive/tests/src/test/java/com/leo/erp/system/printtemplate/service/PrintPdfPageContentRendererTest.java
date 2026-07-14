package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PrintPdfPageContentRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
    private final PrintPdfFormValueResolver valueResolver = new PrintPdfFormValueResolver(runtimeProperties);

    @Test
    void shouldSkipInvalidBlankAndUnpositionedFields() throws Exception {
        PrintPdfDrawingSupport drawing = Mockito.spy(new PrintPdfDrawingSupport());
        PrintPdfPageContentRenderer renderer = new PrintPdfPageContentRenderer(valueResolver, drawing);

        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            var metrics = new PrintPdfDrawingSupport.PageMetrics(595f, 842f);

            renderer.drawFields(canvas, objectMapper.readTree("[]"), Map.of("name", "测试"), font, metrics);
            Map<String, String> nullableData = new HashMap<>();
            nullableData.put("nullValue", null);
            renderer.drawFields(canvas, objectMapper.readTree("""
                    {
                      "nullValue": {"source": "nullValue", "left": 10, "top": 10, "width": 60, "height": 20}
                    }
                    """), nullableData, font, metrics);
            renderer.drawFields(canvas, objectMapper.readTree("""
                    {
                      "blank": {"source": "blank", "left": 10, "top": 10, "width": 60, "height": 20},
                      "missingPosition": {"source": "name", "left": 10, "top": 10},
                      "name": {"source": "name", "left": 10, "top": 40, "width": 60, "height": 20}
                    }
                    """), Map.of("blank", " ", "name", "Alpha"), font, metrics);
        });

        assertThat(bytes).isNotEmpty();
        verify(drawing, never()).drawText(any(), any(), eq("nullValue"), any(), any(), any());
        verify(drawing, never()).drawText(any(), any(), eq("blank"), any(), any(), any());
        verify(drawing, never()).drawText(any(), any(), eq("missingPosition"), any(), any(), any());
        verify(drawing).drawText(any(), any(), eq("name"), any(), eq("Alpha"), any());
    }

    @Test
    void shouldDrawStaticRectLineAndTextWithFallbacks() throws Exception {
        PrintPdfDrawingSupport drawing = Mockito.spy(new PrintPdfDrawingSupport());
        PrintPdfPageContentRenderer renderer = new PrintPdfPageContentRenderer(valueResolver, drawing);

        byte[] bytes = writePdf(canvas -> {
            var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            var metrics = new PrintPdfDrawingSupport.PageMetrics(595f, 842f);
            renderer.drawStatic(
                    canvas,
                    font,
                    objectMapper.readTree("""
                            [
                              {"type":"rect","left":10,"top":10,"width":50,"height":20,"fillColor":"#1677ff"},
                              {"type":"line","x1":10,"y1":40,"x2":70,"y2":40},
                              {"type":"text","text":"合计 ${total}","left":10,"top":60,"width":80,"height":20,"align":"right","color":"#333333"},
                              {"type":null,"text":"默认 ${total}","left":10,"top":90,"width":80},
                              {"type":"image","text":"忽略","left":10,"top":120,"width":80}
                            ]
                            """),
                    Map.of("total", "100"),
                    metrics
            );
        });

        assertThat(bytes).isNotEmpty();
        verify(drawing).drawRect(any(), eq(10f), eq(10f), eq(50f), eq(20f), any(), any());
        verify(drawing).drawLine(any(), any(), any());
        verify(drawing, Mockito.times(2)).drawCanvasText(
                any(),
                any(),
                any(),
                anyFloat(),
                anyFloat(),
                anyFloat(),
                anyFloat(),
                anyFloat(),
                any(),
                any(Color.class),
                any()
        );
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
