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
    private final PrintPdfGroupPaginator groupPaginator;

    public PrintPdfFormRenderer(PrintPdfFormValueResolver valueResolver,
                                PrintPdfFontFactory fontFactory,
                                PrintPdfDrawingSupport drawing,
                                PrintPdfPageContentRenderer pageContentRenderer,
                                PrintPdfTableRenderer tableRenderer,
                                PrintPdfGroupPaginator groupPaginator) {
        this.valueResolver = valueResolver;
        this.fontFactory = fontFactory;
        this.drawing = drawing;
        this.pageContentRenderer = pageContentRenderer;
        this.tableRenderer = tableRenderer;
        this.groupPaginator = groupPaginator;
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
        float tableBottom = drawing.number(tableConfig, "bottom", pageMetrics.height() - 28f);
        float rowHeight = drawing.number(tableConfig, "rowHeight", 26f);
        float headerHeight = drawing.number(tableConfig, "headerHeight", 28f);
        int maxRowsPerPage = Math.max(1, drawing.integer(tableConfig, "maxRowsPerPage", 16));
        List<JsonNode> columns = drawing.childObjects(tableConfig.path("columns"));
        boolean renderTable = tableConfig.isObject();
        boolean repeatHeaderPerSourceGroup = drawing.bool(tableConfig, "repeatHeaderPerSourceGroup", false);
        boolean detailHeaderAfterProject = groupPaginator.isDetailHeaderAfterProject(
                tableConfig, repeatHeaderPerSourceGroup
        );
        boolean repeatGroupContextOnContinuation = drawing.bool(
                tableConfig, "repeatGroupContextOnContinuation", true
        );
        float sourceGroupGap = Math.max(0f, drawing.number(tableConfig, "sourceGroupGap", 0f));

        int itemIndex = 0;
        boolean firstPage = true;
        do {
            PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
            if (firstPage || repeatHeader) {
                pageContentRenderer.drawStatic(canvas, font, root.path("static"), variables, pageMetrics);
                pageContentRenderer.drawFields(canvas, fieldsConfig, data, font, pageMetrics);
            }
            boolean renderHeader = renderTable && firstPage && !repeatHeaderPerSourceGroup;
            float currentTableTop = firstPage ? tableTop : continuationTableTop;
            float rowTop = currentTableTop + (renderHeader ? headerHeight : 0);
            int rowsOnPage = 0;
            if (renderHeader) {
                tableRenderer.drawHeader(canvas, font, tableConfig, columns, currentTableTop, pageMetrics);
            }
            if (repeatHeaderPerSourceGroup && groupPaginator.isGroupContinuation(items, itemIndex)) {
                Map<String, String> continuationItem = items.get(itemIndex);
                boolean continuationStartsProjectGroup = groupPaginator.isProjectGroupHeader(continuationItem);
                if (!repeatGroupContextOnContinuation
                        && (!detailHeaderAfterProject || !continuationStartsProjectGroup)) {
                    tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                    rowTop += headerHeight;
                } else if (repeatGroupContextOnContinuation) {
                    if (!detailHeaderAfterProject) {
                        tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                        rowTop += headerHeight;
                    }
                    for (Map<String, String> header : groupPaginator.groupContinuationHeaders(items, itemIndex)) {
                        tableRenderer.drawItemRow(canvas, font, tableConfig, columns, rowTop, header, pageMetrics);
                        rowTop += groupPaginator.groupHeaderHeight(tableConfig, header);
                    }
                    if (detailHeaderAfterProject && !continuationStartsProjectGroup) {
                        tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                        rowTop += headerHeight;
                    }
                }
            }
            firstPage = false;
            boolean lastPage = true;
            while (renderTable && itemIndex < items.size()) {
                Map<String, String> item = items.get(itemIndex);
                if (groupPaginator.isBlankRow(item)) {
                    boolean hasContent = rowTop > currentTableTop;
                    if (hasContent
                            && itemIndex + 1 < items.size()
                            && groupPaginator.shouldStartGroupOnNextPage(
                            items,
                            itemIndex + 1,
                            rowTop + sourceGroupGap,
                            tableBottom,
                            continuationTableTop,
                            tableConfig,
                            headerHeight,
                            rowHeight,
                            repeatHeaderPerSourceGroup
                    )) {
                        lastPage = false;
                        break;
                    }
                    if (hasContent) {
                        rowTop += sourceGroupGap;
                    }
                    itemIndex++;
                    continue;
                }
                if (groupPaginator.shouldStartGroupOnNextPage(
                        items,
                        itemIndex,
                        rowTop,
                        tableBottom,
                        continuationTableTop,
                        tableConfig,
                        headerHeight,
                        rowHeight,
                        repeatHeaderPerSourceGroup
                )) {
                    lastPage = false;
                    break;
                }
                boolean groupHeader = groupPaginator.isGroupHeader(item);
                float itemHeight = groupHeader
                        ? groupPaginator.groupHeaderHeight(tableConfig, item)
                        : rowHeight;
                float followingHeaderHeight = detailHeaderAfterProject
                        && groupPaginator.isProjectGroupHeader(item) ? headerHeight : 0f;
                if (rowsOnPage >= maxRowsPerPage
                        || rowTop + itemHeight + followingHeaderHeight > tableBottom) {
                    lastPage = false;
                    break;
                }
                if (repeatHeaderPerSourceGroup
                        && !detailHeaderAfterProject
                        && groupPaginator.isSourceGroupHeader(item)) {
                    tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                    rowTop += headerHeight;
                }
                tableRenderer.drawItemRow(canvas, font, tableConfig, columns, rowTop, item, pageMetrics);
                rowTop += itemHeight;
                if (detailHeaderAfterProject && groupPaginator.isProjectGroupHeader(item)) {
                    tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                    rowTop += headerHeight;
                }
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
                rowTop = tableRenderer.drawSummary(
                        canvas, font, root.path("summary"), tableConfig, variables, rowTop, pageMetrics
                );
                tableRenderer.drawClauses(canvas, font, root.path("clauses"), tableConfig, rowTop, pageMetrics);
            }
            canvas.release();
            if (lastPage) {
                break;
            }
        } while (true);
    }

}
