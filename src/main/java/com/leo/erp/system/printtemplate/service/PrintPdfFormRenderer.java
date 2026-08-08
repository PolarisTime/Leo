package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;

@Service
public class PrintPdfFormRenderer {

    private static final String GROUP_HEADER_SOURCE = "source";
    private static final String GROUP_HEADER_PROJECT = "project";
    private static final String GROUP_HEADER_KEY = "isGroupHeader";

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
            PdfFont font = fontFactory.createDefaultFont();
            drawContent(pdf, font, payload.root(), payload.data(), payload.items());
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
            List<Map<String, String>> items
    ) {
        JsonNode tableConfig = root.path("table");
        JsonNode fieldsConfig = root.path("fields");
        if (!tableConfig.isObject() && !fieldsConfig.isObject() && !root.path("static").isArray()) {
            return;
        }

        Map<String, String> variables = valueResolver.summaryVariables(data, items);

        JsonNode pageConfig = root.path("page");
        PrintPdfDrawingSupport.PageMetrics pageMetrics = drawing.pageMetrics(pageConfig);
        boolean repeatHeader = drawing.bool(pageConfig, "repeatHeader", true);
        float tableTop = drawing.number(tableConfig, "top", 176f);
        float continuationTableTop = drawing.number(tableConfig, "continuationTop", tableTop);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        float headerHeight = drawing.number(tableConfig, "headerHeight", 28f);
        int maxRowsPerPage = Math.max(1, drawing.integer(tableConfig, "maxRowsPerPage", 16));
        List<JsonNode> columns = drawing.childObjects(tableConfig.path("columns"));
        boolean renderTable = tableConfig.isObject();

        int itemIndex = 0;
        boolean firstPage = true;
        do {
            PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
            if (firstPage || repeatHeader) {
                pageContentRenderer.drawStatic(canvas, font, root.path("static"), variables, pageMetrics);
                pageContentRenderer.drawFields(canvas, fieldsConfig, data, font, pageMetrics);
            }
            boolean renderHeader = renderTable && firstPage;
            float currentTableTop = firstPage ? tableTop : continuationTableTop;
            float rowTop = currentTableTop + (renderHeader ? headerHeight : 0);
            int rowsOnPage = 0;
            if (renderHeader) {
                tableRenderer.drawHeader(canvas, font, tableConfig, columns, pageMetrics);
            }
            firstPage = false;
            boolean lastPage = true;
            while (renderTable && itemIndex < items.size()) {
                Map<String, String> item = items.get(itemIndex);
                boolean groupHeader = isGroupHeader(item);
                if (rowsOnPage >= maxRowsPerPage) {
                    lastPage = false;
                    break;
                }
                tableRenderer.drawItemRow(canvas, font, tableConfig, columns, rowTop, item, pageMetrics);
                rowTop += groupHeader ? groupHeaderHeight(tableConfig) : rowHeight;
                if (!groupHeader) {
                    rowsOnPage++;
                }
                itemIndex++;
            }
            if (renderTable && items.isEmpty()) {
                tableRenderer.drawNoContentRow(canvas, font, tableConfig, rowTop, pageMetrics);
                rowTop += rowHeight;
            }
            if (renderTable && lastPage) {
                rowTop = tableRenderer.drawSummary(canvas, font, root.path("summary"), tableConfig, variables, rowTop, pageMetrics);
                tableRenderer.drawClauses(canvas, font, root.path("clauses"), tableConfig, rowTop, pageMetrics);
            }
            canvas.release();
            if (lastPage) {
                break;
            }
        } while (true);
    }

    private boolean isGroupHeader(Map<String, String> item) {
        return GROUP_HEADER_SOURCE.equals(item.get(GROUP_HEADER_KEY))
                || GROUP_HEADER_PROJECT.equals(item.get(GROUP_HEADER_KEY));
    }

    private float groupHeaderHeight(JsonNode tableConfig) {
        return drawing.number(tableConfig.path("groupHeader"), "height", 18f);
    }

}
