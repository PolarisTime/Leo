package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PrintPdfFormRenderer {

    private static final float DEFAULT_TABLE_TOP = 176f;
    private static final float DEFAULT_BOTTOM_MARGIN = 36f;

    private final PrintPdfFormValueResolver valueResolver;
    private final PrintPdfFontFactory fontFactory;
    private final PrintPdfDrawingSupport drawing;
    private final PrintPdfPageContentRenderer pageContentRenderer;
    private final PrintPdfTableRenderer tableRenderer;

    public PrintPdfFormRenderer(PrintPdfFormValueResolver valueResolver,
                                PrintPdfFontFactory fontFactory,
                                PrintPdfDrawingSupport drawing,
                                PrintPdfPageContentRenderer pageContentRenderer,
                                PrintPdfTableRenderer tableRenderer) {
        this.valueResolver = valueResolver;
        this.fontFactory = fontFactory;
        this.drawing = drawing;
        this.pageContentRenderer = pageContentRenderer;
        this.tableRenderer = tableRenderer;
    }

    byte[] render(PrintPdfFormPayload payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            PdfFont font = fontFactory.createChineseFont();
            drawContent(pdf, font, payload.root(), payload.data(), payload.items(), payload.sections());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 PDF 表单失败");
        }
        return out.toByteArray();
    }

    private void drawContent(
            PdfDocument pdf,
            PdfFont font,
            JsonNode root,
            Map<String, String> data,
            List<Map<String, String>> items,
            Map<String, List<Map<String, String>>> sections
    ) {
        List<JsonNode> tableConfigs = tableConfigs(root);
        JsonNode fieldsConfig = root.path("fields");
        if (tableConfigs.isEmpty() && !fieldsConfig.isObject() && !root.path("static").isArray()) {
            return;
        }

        PdfFont latinFont = fontFactory.createLatinFont(font);
        Map<String, String> variables = valueResolver.summaryVariables(data, items, sections);
        if (variables == null) {
            variables = valueResolver.summaryVariables(data, items);
        }
        if (variables == null) {
            variables = Map.of();
        }

        PrintPdfDrawingSupport.PageMetrics pageMetrics = drawing.pageMetrics(root.path("page"));
        PdfCanvas canvas = newPage(pdf, font, root, fieldsConfig, data, variables, pageMetrics);
        JsonNode lastTable = null;
        float nextTop = 0f;
        float contentBottom = contentBottom(root, pageMetrics);

        for (JsonNode tableConfig : tableConfigs) {
            List<Map<String, String>> rows = rows(tableConfig, items, sections);
            if (rows.isEmpty() && !bool(tableConfig, "emptyVisible", true)) {
                continue;
            }
            float tableTop = tableConfig.has("top")
                    ? drawing.number(tableConfig, "top", DEFAULT_TABLE_TOP)
                    : (nextTop <= 0f ? DEFAULT_TABLE_TOP : nextTop + drawing.number(tableConfig, "marginTop", 8f));
            JsonNode activeTable = tableAtTop(tableConfig, tableTop);
            float rowHeight = drawing.number(activeTable, "rowHeight", 26f);
            float headerHeight = drawing.number(activeTable, "headerHeight", 28f);
            float titleHeight = titleHeight(activeTable);
            int maxRowsPerPage = Math.max(1, drawing.integer(activeTable, "maxRowsPerPage", 16));
            List<JsonNode> columns = drawing.childObjects(activeTable.path("columns"));

            if (rows.isEmpty()) {
                if (!fits(tableTop, titleHeight + headerHeight + rowHeight, contentBottom)) {
                    canvas.release();
                    canvas = newPage(pdf, font, root, fieldsConfig, data, variables, pageMetrics);
                    tableTop = resetTop(tableConfig);
                    activeTable = tableAtTop(tableConfig, tableTop);
                }
                tableRenderer.drawHeader(canvas, font, activeTable, columns, pageMetrics);
                nextTop = tableTop + titleHeight + headerHeight;
                tableRenderer.drawNoContentRow(canvas, font, activeTable, nextTop, pageMetrics);
                nextTop += rowHeight;
                lastTable = activeTable;
                continue;
            }

            int rowIndex = 0;
            while (rowIndex < rows.size()) {
                boolean drawHeader = rowIndex == 0 || bool(activeTable, "repeatHeader", true);
                float headerBlockHeight = drawHeader ? titleHeight + headerHeight : 0f;
                if (!fits(tableTop, headerBlockHeight + rowHeight, contentBottom)) {
                    canvas.release();
                    canvas = newPage(pdf, font, root, fieldsConfig, data, variables, pageMetrics);
                    tableTop = resetTop(tableConfig);
                    activeTable = tableAtTop(tableConfig, tableTop);
                    drawHeader = true;
                    headerBlockHeight = titleHeight(activeTable) + drawing.number(activeTable, "headerHeight", 28f);
                }
                if (drawHeader) {
                    tableRenderer.drawHeader(canvas, font, activeTable, columns, pageMetrics);
                }
                int rowsOnPage = rowsOnPage(rows.size() - rowIndex, maxRowsPerPage, rowHeight, tableTop, headerBlockHeight, contentBottom);
                for (int row = 0; row < rowsOnPage; row++) {
                    tableRenderer.drawItemRow(
                            canvas,
                            font,
                            latinFont,
                            activeTable,
                            columns,
                            tableTop + headerBlockHeight + row * rowHeight,
                            rows.get(rowIndex + row),
                            pageMetrics
                    );
                }
                rowIndex += rowsOnPage;
                nextTop = tableTop + headerBlockHeight + rowsOnPage * rowHeight;
                lastTable = activeTable;
                if (rowIndex < rows.size()) {
                    canvas.release();
                    canvas = newPage(pdf, font, root, fieldsConfig, data, variables, pageMetrics);
                    tableTop = resetTop(tableConfig);
                    activeTable = tableAtTop(tableConfig, tableTop);
                }
            }
        }

        if (lastTable != null) {
            float trailingHeight = summaryHeight(root.path("summary"), lastTable)
                    + clausesHeight(root.path("clauses"));
            if (trailingHeight > 0f && !fits(nextTop, trailingHeight, contentBottom)) {
                canvas.release();
                canvas = newPage(pdf, font, root, fieldsConfig, data, variables, pageMetrics);
                nextTop = number(root.path("summary"), "top", resetTop(lastTable));
            }
            nextTop = tableRenderer.drawSummary(canvas, font, root.path("summary"), lastTable, variables, nextTop, pageMetrics);
            tableRenderer.drawClauses(canvas, font, root.path("clauses"), lastTable, nextTop, pageMetrics);
        }
        canvas.release();
    }

    private PdfCanvas newPage(
            PdfDocument pdf,
            PdfFont font,
            JsonNode root,
            JsonNode fieldsConfig,
            Map<String, String> data,
            Map<String, String> variables,
            PrintPdfDrawingSupport.PageMetrics pageMetrics
    ) {
        PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
        pageContentRenderer.drawStatic(canvas, font, root.path("static"), variables, pageMetrics);
        pageContentRenderer.drawFields(canvas, fieldsConfig, data, font, pageMetrics);
        return canvas;
    }

    private List<JsonNode> tableConfigs(JsonNode root) {
        List<JsonNode> tables = new ArrayList<>();
        if (root.path("tables").isArray()) {
            for (JsonNode table : root.path("tables")) {
                if (table.isObject()) {
                    tables.add(table);
                }
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        }
        JsonNode table = root.path("table");
        return table.isObject() ? List.of(table) : List.of();
    }

    private List<Map<String, String>> rows(
            JsonNode tableConfig,
            List<Map<String, String>> items,
            Map<String, List<Map<String, String>>> sections
    ) {
        String source = text(tableConfig, "source", PrintRecordData.ITEMS_SECTION);
        if (PrintRecordData.ITEMS_SECTION.equals(source)) {
            return items == null ? List.of() : items;
        }
        if (sections == null) {
            return List.of();
        }
        return sections.getOrDefault(source, List.of());
    }

    private JsonNode tableAtTop(JsonNode tableConfig, float tableTop) {
        if (!tableConfig.isObject() || tableConfig.has("top")) {
            return tableConfig;
        }
        ObjectNode copy = tableConfig.deepCopy();
        copy.put("top", tableTop);
        return copy;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : fallback;
    }

    private float titleHeight(JsonNode tableConfig) {
        return text(tableConfig, "title", "").isBlank()
                ? 0f
                : drawing.number(tableConfig, "titleHeight", 22f);
    }

    private int rowsOnPage(int remainingRows, int maxRowsPerPage, float rowHeight, float tableTop, float headerBlockHeight, float contentBottom) {
        float availableHeight = Math.max(rowHeight, contentBottom - tableTop - headerBlockHeight);
        int rowsByHeight = Math.max(1, (int) Math.floor(availableHeight / rowHeight));
        return Math.min(remainingRows, Math.min(maxRowsPerPage, rowsByHeight));
    }

    private boolean fits(float top, float height, float contentBottom) {
        return top + height <= contentBottom;
    }

    private float contentBottom(JsonNode root, PrintPdfDrawingSupport.PageMetrics pageMetrics) {
        return pageMetrics.height() - number(root.path("page"), "bottomMargin", DEFAULT_BOTTOM_MARGIN);
    }

    private float resetTop(JsonNode tableConfig) {
        return number(tableConfig, "pageResetTop", number(tableConfig, "top", DEFAULT_TABLE_TOP));
    }

    private float summaryHeight(JsonNode summaryConfig, JsonNode tableConfig) {
        if (!summaryConfig.isObject()) {
            return 0f;
        }
        return number(summaryConfig, "height", number(tableConfig, "rowHeight", 26f));
    }

    private float clausesHeight(JsonNode clausesConfig) {
        if (!clausesConfig.isObject() || !hasVisibleClauseLines(clausesConfig.path("lines"))) {
            return 0f;
        }
        return number(clausesConfig, "paddingTop", 8f) + number(clausesConfig, "height", 96f);
    }

    private boolean hasVisibleClauseLines(JsonNode lines) {
        if (!lines.isArray()) {
            return false;
        }
        for (JsonNode line : lines) {
            if (!line.asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private float number(JsonNode node, String field, float fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? (float) child.asDouble() : fallback;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }
}
