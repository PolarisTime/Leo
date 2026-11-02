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
import java.util.Set;

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
                    top,
                    width - 4,
                    headerHeight,
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
        drawItemRow(canvas, font, tableConfig, columns, top, item, Map.of(), Set.of(), pageMetrics);
    }

    /**
     * 带纵向合并的明细行绘制：mergeSpans 表示当前行在某个列上要向下合并几行（1 表示不合并），
     * mergeColumns 中的列在值为空时直接跳过（由首行画出的高单元格覆盖），从而得到真正的合并单元格。
     */
    void drawItemRow(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode tableConfig,
            List<JsonNode> columns,
            float top,
            Map<String, String> item,
            Map<String, Integer> mergeSpans,
            Set<String> mergeColumns,
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
            String columnKey = text(column, "key", "");
            float fontSize = drawing.number(column, "fontSize", 8f);
            if (mergeColumns.contains(columnKey)) {
                String mergedValue = valueResolver.itemValue(item, column);
                if (mergedValue.isBlank()) {
                    // 合并列的续行：单元格由首行的高单元格覆盖，这里既不画边框也不画文字
                    left += width;
                    continue;
                }
                int span = Math.max(1, mergeSpans.getOrDefault(columnKey, 1));
                float mergedHeight = rowHeight * span;
                drawing.drawRect(
                        canvas,
                        left,
                        top,
                        width,
                        mergedHeight,
                        null,
                        drawing.color(column, "borderColor", borderColor),
                        drawing.number(column, "lineWidth", lineWidth),
                        pageMetrics
                );
                float availWidth = Math.max(4f, width - 4);
                if (font.getWidth(mergedValue, fontSize) > availWidth) {
                    drawWrappedCell(canvas, font, column, left, top, width, mergedHeight,
                            mergedValue, fontSize, availWidth, textColor, pageMetrics);
                } else {
                    drawing.drawCanvasText(
                            canvas,
                            font,
                            mergedValue,
                            left + 2,
                            top,
                            width - 4,
                            mergedHeight,
                            fontSize,
                            drawing.alignment(text(column, "align", "center")),
                            drawing.color(column, "textColor", textColor),
                            pageMetrics
                    );
                }
                left += width;
                continue;
            }
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
                        top,
                        width - 4,
                        rowHeight,
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
        float blockHeight = lines.size() * lineHeight;
        float y = top + Math.max(0f, (rowHeight - blockHeight) / 2f);
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
        List<String> lines = summaryLines(summaryConfig);
        boolean alignToColumns = drawing.bool(summaryConfig, "alignToColumns", false);
        if (lines.isEmpty() && !alignToColumns) {
            return top;
        }
        float left = drawing.number(tableConfig, "left", 28f);
        float width = drawing.tableWidth(tableConfig);
        // height 为单行汇总行高，多行汇总按行数向下堆叠。
        float rowHeight = drawing.number(summaryConfig, "height", drawing.number(tableConfig, "rowHeight", 26f));
        boolean border = drawing.bool(summaryConfig, "border", true);
        Color borderColor = drawing.color(
                summaryConfig, "borderColor", drawing.color(tableConfig, "borderColor", ColorConstants.BLACK)
        );
        float lineWidth = drawing.number(summaryConfig, "lineWidth", drawing.number(tableConfig, "lineWidth", 1f));
        Color fillColor = drawing.color(summaryConfig, "fillColor", null);
        float paddingLeft = drawing.number(summaryConfig, "paddingLeft", 6f);
        float paddingTop = drawing.number(summaryConfig, "paddingTop", 7f);
        float fontSize = drawing.number(summaryConfig, "fontSize", 8.5f);
        TextAlignment alignment = drawing.alignment(text(summaryConfig, "align", "left"));
        Color color = drawing.color(summaryConfig, "color", drawing.color(summaryConfig, "textColor", ColorConstants.BLACK));

        float lineTop = top;
        for (String line : lines) {
            if (border) {
                drawing.drawRect(canvas, left, lineTop, width, rowHeight, fillColor, borderColor, lineWidth, pageMetrics);
            }
            drawing.drawCanvasText(
                    canvas,
                    font,
                    valueResolver.applyTemplate(
                            line,
                            variables,
                            summaryConfig.path("formats"),
                            summaryConfig.path("defaults")
                    ),
                    left + paddingLeft,
                    lineTop,
                    width - paddingLeft * 2,
                    rowHeight,
                    fontSize,
                    alignment,
                    color,
                    pageMetrics
            );
            lineTop += rowHeight;
        }
        if (alignToColumns) {
            lineTop = drawSummaryColumnRow(
                    canvas, font, summaryConfig, tableConfig, variables, left, lineTop, rowHeight,
                    fillColor, borderColor, lineWidth, fontSize, pageMetrics);
        }
        lineTop = drawSummaryGrid(canvas, font, summaryConfig, variables, left, width, lineTop, lineWidth,
                borderColor, color, fontSize, pageMetrics);
        return lineTop;
    }

    /**
     * 合计行按明细列对齐：每个合计值落在对应的列宽里（件数/重量/金额），
     * 文字居中、列宽直接取 table.columns 的宽度，从而随明细列自动拉伸。
     */
    private float drawSummaryColumnRow(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode summaryConfig,
            JsonNode tableConfig,
            Map<String, String> variables,
            float left,
            float top,
            float rowHeight,
            Color fillColor,
            Color borderColor,
            float lineWidth,
            float fontSize,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        List<JsonNode> columns = drawing.childObjects(tableConfig.path("columns"));
        JsonNode cells = summaryConfig.path("cells");
        float cellLeft = left;
        for (JsonNode column : columns) {
            float cellWidth = drawing.number(column, "width", 60f);
            String template = text(cells, text(column, "key", ""), "");
            String value = valueResolver.applyTemplate(
                    template,
                    variables,
                    summaryConfig.path("formats"),
                    summaryConfig.path("defaults")
            );
            drawing.drawRect(canvas, cellLeft, top, cellWidth, rowHeight, fillColor, borderColor, lineWidth, pageMetrics);
            drawing.drawCanvasText(
                    canvas,
                    font,
                    value,
                    cellLeft + 4,
                    top,
                    cellWidth - 8,
                    rowHeight,
                    fontSize,
                    TextAlignment.CENTER,
                    drawing.color(summaryConfig, "color", drawing.color(summaryConfig, "textColor", ColorConstants.BLACK)),
                    pageMetrics
            );
            cellLeft += cellWidth;
        }
        return top + rowHeight;
    }

    /**
     * 汇总区内的"累计"信息块：按 2 x N 网格画带边框的标签/数值单元格，
     * 与参考对账单底部的 累计数量合计 / 累计应收金额 等行一致。
     */
    private float drawSummaryGrid(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode summaryConfig,
            Map<String, String> variables,
            float left,
            float width,
            float top,
            float lineWidth,
            Color borderColor,
            Color color,
            float fontSize,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        JsonNode grid = summaryConfig.path("grid");
        if (!grid.isObject()) {
            return top;
        }
        JsonNode rows = grid.path("rows");
        JsonNode widths = grid.path("columnWidths");
        float rowHeight = drawing.number(grid, "rowHeight", 24f);
        float gridFontSize = drawing.number(grid, "fontSize", fontSize);
        Color gridBorder = drawing.color(grid, "borderColor", borderColor);
        Color gridColor = drawing.color(grid, "color", color);
        float lineTop = top;
        for (JsonNode row : rows) {
            float cellLeft = left;
            for (int index = 0; index < row.size(); index++) {
                float cellWidth = index < widths.size() ? (float) widths.get(index).asDouble(80d) : 80f;
                String cellValue = valueResolver.applyTemplate(
                        row.get(index).asText(""),
                        variables,
                        summaryConfig.path("formats"),
                        summaryConfig.path("defaults")
                );
                drawing.drawRect(canvas, cellLeft, lineTop, cellWidth, rowHeight, null, gridBorder, lineWidth, pageMetrics);
                drawing.drawCanvasText(
                        canvas,
                        font,
                        cellValue,
                        cellLeft + 4,
                        lineTop,
                        cellWidth - 8,
                        rowHeight,
                        gridFontSize,
                        TextAlignment.CENTER,
                        gridColor,
                        pageMetrics
                );
                cellLeft += cellWidth;
            }
            if (cellLeft < left + width) {
                drawing.drawRect(canvas, cellLeft, lineTop, left + width - cellLeft, rowHeight, null,
                        gridBorder, lineWidth, pageMetrics);
            }
            lineTop += rowHeight;
        }
        return lineTop;
    }

    /** 汇总行支持 lines 数组渲染多行；未配置时回退为单行 template。 */
    private List<String> summaryLines(JsonNode summaryConfig) {
        List<String> configured = drawing.childTextValues(summaryConfig.path("lines"));
        if (!configured.isEmpty()) {
            return configured;
        }
        String template = text(summaryConfig, "template", "");
        return template.isBlank() ? List.of() : List.of(template);
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
