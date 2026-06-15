package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

@Service
public class PrintPdfPageContentRenderer {

    private final PrintPdfFormValueResolver valueResolver;
    private final PrintPdfDrawingSupport drawing;

    public PrintPdfPageContentRenderer(PrintPdfFormValueResolver valueResolver,
                                       PrintPdfDrawingSupport drawing) {
        this.valueResolver = valueResolver;
        this.drawing = drawing;
    }

    void drawFields(
            PdfCanvas canvas,
            JsonNode fieldsConfig,
            Map<String, String> data,
            PdfFont font,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        if (!fieldsConfig.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> configuredFields = fieldsConfig.fields();
        while (configuredFields.hasNext()) {
            Map.Entry<String, JsonNode> entry = configuredFields.next();
            String fieldName = entry.getKey();
            JsonNode fieldConfig = entry.getValue();
            String value = valueResolver.fieldValue(data, fieldConfig, fieldName);
            if (value == null || value.isBlank()) {
                continue;
            }
            Rectangle rectangle = drawing.configuredRectangle(fieldConfig, pageMetrics);
            if (rectangle != null) {
                drawing.drawText(canvas, rectangle, fieldName, fieldConfig, value, font);
            }
        }
    }

    void drawStatic(PdfCanvas canvas, PdfFont font, JsonNode staticConfig, PrintPdfDrawingSupport.PageMetrics pageMetrics) {
        for (JsonNode element : drawing.childObjects(staticConfig)) {
            String type = text(element, "type", "text").trim().toLowerCase();
            if ("rect".equals(type)) {
                drawing.drawRect(
                        canvas,
                        drawing.number(element, "left", 0f),
                        drawing.number(element, "top", 0f),
                        drawing.number(element, "width", 1f),
                        drawing.number(element, "height", 1f),
                        element,
                        pageMetrics
                );
                continue;
            }
            if ("line".equals(type)) {
                drawing.drawLine(canvas, element, pageMetrics);
                continue;
            }
            if ("text".equals(type)) {
                drawing.drawCanvasText(
                        canvas,
                        font,
                        text(element, "text", ""),
                        drawing.number(element, "left", 0f),
                        drawing.number(element, "top", 0f),
                        drawing.number(element, "width", 100f),
                        drawing.number(element, "height", drawing.number(element, "fontSize", 9f) + 4f),
                        drawing.number(element, "fontSize", 9f),
                        drawing.alignment(text(element, "align", "left")),
                        drawing.color(element, "color", ColorConstants.BLACK),
                        pageMetrics
                );
            }
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }
}
