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

    private static final String GROUP_HEADER_SOURCE = "source";
    private static final String GROUP_HEADER_PROJECT = "project";
    private static final String GROUP_HEADER_SUBTOTAL = "subtotal";
    private static final String GROUP_HEADER_KEY = "isGroupHeader";

    private final PrintPdfFormValueResolver valueResolver;
    private final PrintPdfDrawingSupport drawing;

    public PrintPdfTableRenderer(PrintPdfFormValueResolver valueResolver,
                                 PrintPdfDrawingSupport drawing) {
        this.valueResolver = valueResolver;
        this.drawing = drawing;
    }

    void drawHeader(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode tableConfig,
            List<JsonNode> columns,
            float top,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        float left = drawing.number(tableConfig, "left", 28f);
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
            JsonNode tableConfig,
            List<JsonNode> columns,
            float top,
            Map<String, String> item,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        if (GROUP_HEADER_SOURCE.equals(item.get(GROUP_HEADER_KEY))
                || GROUP_HEADER_PROJECT.equals(item.get(GROUP_HEADER_KEY))
                || GROUP_HEADER_SUBTOTAL.equals(item.get(GROUP_HEADER_KEY))) {
            drawGroupHeaderRow(canvas, font, tableConfig, top, item, pageMetrics);
            return;
        }
        if ("true".equals(item.get("isBlankRow"))) {
            // 空白分隔行：只占位推进行高，不绘制内容
            return;
        }
        float left = drawing.number(tableConfig, "left", 28f);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        Color borderColor = drawing.color(tableConfig, "borderColor", ColorConstants.BLACK);
        Color textColor = drawing.color(tableConfig, "textColor", ColorConstants.BLACK);
        float lineWidth = drawing.number(tableConfig, "lineWidth", 1f);
        for (JsonNode column : columns) {
            float width = drawing.number(column, "width", 60f);
            String value = valueResolver.itemValue(item, column);
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
            float fontSize = drawing.number(column, "fontSize", 8f);
            float availWidth = Math.max(4f, width - 4);
            if (font.getWidth(value, fontSize) > availWidth) {
                drawWrappedCell(canvas, font, column, left, top, width, rowHeight,
                        value, fontSize, availWidth, textColor, pageMetrics);
            } else {
                drawing.drawCanvasText(
                        canvas,
                        font,
                        value,
                        left + 2,
                        top + 7,
                        width - 4,
                        12,
                        fontSize,
                        drawing.alignment(text(column, "align", "center")),
                        drawing.color(column, "textColor", textColor),
                        pageMetrics
                );
            }
            left += width;
        }
    }

    private void drawWrappedCell(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode column,
            float left,
            float top,
            float width,
            float rowHeight,
            String value,
            float fontSize,
            float availWidth,
            Color defaultTextColor,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        float lineHeight = Math.max(6f, fontSize * 1.2f);
        int maxLines = Math.max(1, (int) ((rowHeight - 4) / lineHeight));
        List<String> lines = drawing.wrapLines(font, value, fontSize, availWidth);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        TextAlignment alignment = drawing.alignment(text(column, "align", "center"));
        Color color = drawing.color(column, "textColor", defaultTextColor);
        float y = top + 2;
        for (String line : lines) {
            drawing.drawCanvasText(canvas, font, line, left + 2, y, width - 4, lineHeight,
                    fontSize, alignment, color, pageMetrics);
            y += lineHeight;
        }
    }

    void drawGroupHeaderRow(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode tableConfig,
            float top,
            Map<String, String> groupHeader,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        float left = drawing.number(tableConfig, "left", 28f);
        float width = drawing.tableWidth(tableConfig);
        float lineWidth = drawing.number(tableConfig, "lineWidth", 1f);
        Color borderColor = drawing.color(tableConfig, "borderColor", ColorConstants.BLACK);
        JsonNode headerConfig = tableConfig.path("groupHeader");
        String groupType = groupHeader.get(GROUP_HEADER_KEY);
        boolean sourceHeader = GROUP_HEADER_SOURCE.equals(groupType);
        JsonNode groupTypeConfig = headerConfig.path(groupType);
        float rowHeight = drawing.number(
                groupTypeConfig,
                "height",
                drawing.number(
                        headerConfig,
                        sourceHeader ? "sourceHeight" : "projectHeight",
                        drawing.number(headerConfig, "height", 18f)
                )
        );
        Color fillColor = drawing.color(
                groupTypeConfig,
                "fillColor",
                drawing.color(
                        headerConfig,
                        "fillColor",
                        drawing.color(tableConfig, "headerFillColor", ColorConstants.WHITE)
                )
        );
        Color textColor = drawing.color(
                groupTypeConfig,
                "textColor",
                drawing.color(
                        headerConfig,
                        "textColor",
                        drawing.color(tableConfig, "textColor", ColorConstants.BLACK)
                )
        );
        float fontSize = drawing.number(
                groupTypeConfig,
                "fontSize",
                drawing.number(headerConfig, "fontSize", 8.5f)
        );

        drawing.drawRect(canvas, left, top, width, rowHeight, fillColor, borderColor, lineWidth, pageMetrics);
        drawGroupHeaderLines(
                canvas,
                font,
                headerConfig,
                groupTypeConfig,
                groupHeader,
                left,
                top,
                width,
                rowHeight,
                fontSize,
                textColor,
                pageMetrics
        );
    }

    private void drawGroupHeaderLines(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode headerConfig,
            JsonNode groupTypeConfig,
            Map<String, String> variables,
            float left,
            float top,
            float width,
            float rowHeight,
            float fontSize,
            Color textColor,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        List<String> lines = drawing.childTextValues(groupTypeConfig.path("lines"));
        if (lines.isEmpty()) {
            return;
        }
        float paddingLeft = drawing.number(
                groupTypeConfig,
                "paddingLeft",
                drawing.number(headerConfig, "paddingLeft", 4f)
        );
        float paddingRight = drawing.number(
                groupTypeConfig,
                "paddingRight",
                drawing.number(headerConfig, "paddingRight", paddingLeft)
        );
        float paddingTop = drawing.number(
                groupTypeConfig,
                "paddingTop",
                drawing.number(headerConfig, "paddingTop", 2f)
        );
        TextAlignment alignment = drawing.alignment(
                text(groupTypeConfig, "align", text(headerConfig, "align", "left"))
        );
        JsonNode lineAligns = groupTypeConfig.path("lineAligns");
        float lineHeight = rowHeight / lines.size();
        for (int index = 0; index < lines.size(); index++) {
            String line = valueResolver.applyTemplate(
                    lines.get(index),
                    variables,
                    groupTypeConfig.path("formats"),
                    groupTypeConfig.path("defaults")
            );
            drawing.drawCanvasText(
                    canvas,
                    font,
                    line,
                    left + paddingLeft,
                    top + index * lineHeight + paddingTop,
                    width - paddingLeft - paddingRight,
                    Math.max(1f, lineHeight - paddingTop),
                    fontSize,
                    lineAlignment(lineAligns, index, alignment),
                    textColor,
                    pageMetrics
            );
        }
    }

    private TextAlignment lineAlignment(JsonNode lineAligns, int index, TextAlignment fallback) {
        if (!lineAligns.isArray() || index >= lineAligns.size()) {
            return fallback;
        }
        String value = lineAligns.path(index).asText("");
        return value.isBlank() ? fallback : drawing.alignment(value);
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
