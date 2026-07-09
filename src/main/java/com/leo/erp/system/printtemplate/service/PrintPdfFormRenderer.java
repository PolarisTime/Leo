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

        PdfFont latinFont = fontFactory.createLatinFont(font);
        Map<String, String> variables = valueResolver.summaryVariables(data, items);

        PrintPdfDrawingSupport.PageMetrics pageMetrics = drawing.pageMetrics(root.path("page"));
        float tableTop = drawing.number(tableConfig, "top", 176f);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        float headerHeight = drawing.number(tableConfig, "headerHeight", 28f);
        int maxRowsPerPage = Math.max(1, drawing.integer(tableConfig, "maxRowsPerPage", 16));
        List<JsonNode> columns = drawing.childObjects(tableConfig.path("columns"));

        int itemIndex = 0;
        do {
            PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
            pageContentRenderer.drawStatic(canvas, font, root.path("static"), variables, pageMetrics);
            pageContentRenderer.drawFields(canvas, fieldsConfig, data, font, pageMetrics);
            int rowsOnPage = 0;
            if (tableConfig.isObject()) {
                rowsOnPage = Math.min(items.size() - itemIndex, maxRowsPerPage);
                boolean lastPage = itemIndex + rowsOnPage >= items.size();
                tableRenderer.drawHeader(canvas, font, tableConfig, columns, pageMetrics);
                for (int row = 0; row < rowsOnPage; row++) {
                    tableRenderer.drawItemRow(
                            canvas,
                            font,
                            latinFont,
                            tableConfig,
                            columns,
                            tableTop + headerHeight + row * rowHeight,
                            items.get(itemIndex + row),
                            pageMetrics
                    );
                }
                float nextTop = tableTop + headerHeight + rowsOnPage * rowHeight;
                if (items.isEmpty()) {
                    tableRenderer.drawNoContentRow(canvas, font, tableConfig, nextTop, pageMetrics);
                    nextTop += rowHeight;
                }
                if (lastPage) {
                    nextTop = tableRenderer.drawSummary(canvas, font, root.path("summary"), tableConfig, variables, nextTop, pageMetrics);
                    tableRenderer.drawClauses(canvas, font, root.path("clauses"), tableConfig, nextTop, pageMetrics);
                }
            }
            canvas.release();
            itemIndex += rowsOnPage;
        } while (tableConfig.isObject() && itemIndex < items.size());
    }

}
