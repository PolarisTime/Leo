package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PrintPdfTableRenderer {

    private final PrintPdfFormValueResolver valueResolver;
    private final PrintPdfDrawingSupport drawing;

    public PrintPdfTableRenderer(PrintPdfFormValueResolver valueResolver,
                                 PrintPdfDrawingSupport drawing) {
        this.valueResolver = valueResolver;
        this.drawing = drawing;
    }

    void drawHeader(PdfCanvas canvas, PdfFont font, JsonNode tableConfig, List<JsonNode> columns, PrintPdfDrawingSupport.PageMetrics pageMetrics) {
        float left = drawing.number(tableConfig, "left", 28f);
        float top = drawing.number(tableConfig, "top", 176f);
        float headerHeight = drawing.number(tableConfig, "headerHeight", 28f);
        Color headerFillColor = drawing.color(tableConfig, "headerFillColor", null);
        Color borderColor = drawing.color(tableConfig, "borderColor", ColorConstants.BLACK);
        Color textColor = drawing.color(tableConfig, "headerTextColor", drawing.color(tableConfig, "textColor", ColorConstants.BLACK));
        float lineWidth = drawing.number(tableConfig, "lineWidth", 1f);
        for (JsonNode column : columns) {
            float width = drawing.number(column, "width", 60f);
            drawing.drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    headerHeight,
                    headerFillColor,
                    drawing.color(column, "borderColor", borderColor),
                    drawing.number(column, "lineWidth", lineWidth),
                    pageMetrics
            );
            drawing.drawCanvasText(
                    canvas,
                    font,
                    text(column, "label", ""),
                    left + 2,
                    top + 6,
                    width - 4,
                    16,
                    drawing.number(column, "headerFontSize", 9f),
                    TextAlignment.CENTER,
                    drawing.color(column, "headerTextColor", textColor),
                    pageMetrics
            );
            left += width;
        }
    }

    void drawItemRow(
            PdfCanvas canvas,
            PdfFont font,
            PdfFont latinFont,
            JsonNode tableConfig,
            List<JsonNode> columns,
            float top,
            Map<String, String> item,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        float left = drawing.number(tableConfig, "left", 28f);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        Color borderColor = drawing.color(tableConfig, "borderColor", ColorConstants.BLACK);
        Color textColor = drawing.color(tableConfig, "textColor", ColorConstants.BLACK);
        float lineWidth = drawing.number(tableConfig, "lineWidth", 1f);
        for (JsonNode column : columns) {
            float width = drawing.number(column, "width", 60f);
            String value = valueResolver.itemValue(item, column);
            PdfFont cellFont = "latinIfAscii".equals(text(column, "font", "")) && drawing.isAsciiText(value) ? latinFont : font;
            drawing.drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    rowHeight,
                    null,
                    drawing.color(column, "borderColor", borderColor),
                    drawing.number(column, "lineWidth", lineWidth),
                    pageMetrics
            );
            drawing.drawCanvasText(
                    canvas,
                    cellFont,
                    value,
                    left + 2,
                    top + 7,
                    width - 4,
                    12,
                    drawing.number(column, "fontSize", 8f),
                    drawing.alignment(text(column, "align", "center")),
                    drawing.color(column, "textColor", textColor),
                    pageMetrics
            );
            left += width;
        }
    }

    void drawNoContentRow(PdfCanvas canvas, PdfFont font, JsonNode tableConfig, float top, PrintPdfDrawingSupport.PageMetrics pageMetrics) {
        float left = drawing.number(tableConfig, "left", 28f);
        float width = drawing.tableWidth(tableConfig);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        drawing.drawRect(
                canvas,
                left,
                top,
                width,
                rowHeight,
                drawing.color(tableConfig, "emptyFillColor", null),
                drawing.color(tableConfig, "borderColor", ColorConstants.BLACK),
                drawing.number(tableConfig, "lineWidth", 1f),
                pageMetrics
        );
        drawing.drawCanvasText(
                canvas,
                font,
                text(tableConfig, "emptyText", ""),
                left,
                top + 7,
                width,
                12,
                drawing.number(tableConfig, "emptyFontSize", 8f),
                TextAlignment.CENTER,
                drawing.color(tableConfig, "emptyTextColor", drawing.color(tableConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
    }

    float drawSummary(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode summaryConfig,
            JsonNode tableConfig,
            Map<String, String> variables,
            float top,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        if (!summaryConfig.isObject()) {
            return top;
        }
        float left = drawing.number(tableConfig, "left", 28f);
        float width = drawing.tableWidth(tableConfig);
        float height = drawing.number(summaryConfig, "height", drawing.number(tableConfig, "rowHeight", 26f));
        if (drawing.bool(summaryConfig, "border", true)) {
            drawing.drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    height,
                    drawing.color(summaryConfig, "fillColor", null),
                    drawing.color(summaryConfig, "borderColor", drawing.color(tableConfig, "borderColor", ColorConstants.BLACK)),
                    drawing.number(summaryConfig, "lineWidth", drawing.number(tableConfig, "lineWidth", 1f)),
                    pageMetrics
            );
        }
        drawing.drawCanvasText(
                canvas,
                font,
                valueResolver.applyTemplate(text(summaryConfig, "template", ""), variables),
                left + drawing.number(summaryConfig, "paddingLeft", 6f),
                top + drawing.number(summaryConfig, "paddingTop", 7f),
                width - drawing.number(summaryConfig, "paddingLeft", 6f) * 2,
                12,
                drawing.number(summaryConfig, "fontSize", 8.5f),
                drawing.alignment(text(summaryConfig, "align", "left")),
                drawing.color(summaryConfig, "color", drawing.color(summaryConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
        return top + height;
    }

    void drawClauses(PdfCanvas canvas, PdfFont font, JsonNode clausesConfig, JsonNode tableConfig, float top, PrintPdfDrawingSupport.PageMetrics pageMetrics) {
        if (!clausesConfig.isObject()) {
            return;
        }
        List<String> paragraphs = drawing.childTextValues(clausesConfig.path("lines"));
        if (paragraphs.isEmpty()) {
            return;
        }
        float left = drawing.number(clausesConfig, "left", drawing.number(tableConfig, "left", 28f));
        float width = drawing.number(clausesConfig, "width", drawing.tableWidth(tableConfig));
        drawing.drawParagraphs(
                canvas,
                font,
                paragraphs,
                left,
                top + drawing.number(clausesConfig, "paddingTop", 8f),
                width,
                drawing.number(clausesConfig, "height", 96f),
                drawing.number(clausesConfig, "fontSize", 7.8f),
                drawing.number(clausesConfig, "lineHeight", 1.28f),
                drawing.color(clausesConfig, "color", drawing.color(clausesConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }
}
