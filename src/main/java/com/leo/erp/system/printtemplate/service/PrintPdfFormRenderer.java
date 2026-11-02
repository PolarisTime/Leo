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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrintPdfFormRenderer {

    private final PrintPdfFormValueResolver valueResolver;
    private final PrintPdfFontFactory fontFactory;
    private final PrintPdfDrawingSupport drawing;
    private final PrintPdfPageContentRenderer pageContentRenderer;
    private final PrintPdfTableRenderer tableRenderer;
    private final PrintPdfGroupPaginator groupPaginator;
    private final PrintPdfItemGrouper itemGrouper;

    public PrintPdfFormRenderer(
            PrintPdfFormValueResolver valueResolver,
            PrintPdfFontFactory fontFactory,
            PrintPdfDrawingSupport drawing,
            PrintPdfPageContentRenderer pageContentRenderer,
            PrintPdfTableRenderer tableRenderer,
            PrintPdfGroupPaginator groupPaginator,
            PrintPdfItemGrouper itemGrouper
    ) {
        this.valueResolver = valueResolver;
        this.fontFactory = fontFactory;
        this.drawing = drawing;
        this.pageContentRenderer = pageContentRenderer;
        this.tableRenderer = tableRenderer;
        this.groupPaginator = groupPaginator;
        this.itemGrouper = itemGrouper;
    }

    byte[] render(PrintPdfFormPayload payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            PdfFont font = fontFactory.createDefaultFont();
            List<Map<String, String>> items = itemGrouper.group(
                    payload.items(),
                    payload.root().path("table").path("grouping")
            );
            drawContent(pdf, font, payload.root(), payload.data(), items);
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
        /*
         * 每页只画一次表头（含续页），与"每个来源分组前都画表头"区分开：
         * 客户对账单这类按销售单分组、组数多的模板，按组重复表头会把页面切成一段段，
         * 但续页又必须带表头才能读数，于是需要这个独立开关。
         */
        boolean repeatHeaderPerPage = drawing.bool(tableConfig, "repeatHeaderPerPage", false);
        boolean detailHeaderAfterProject = groupPaginator.isDetailHeaderAfterProject(
                tableConfig, repeatHeaderPerSourceGroup
        );
        boolean repeatGroupContextOnContinuation = drawing.bool(
                tableConfig, "repeatGroupContextOnContinuation", true
        );
        float sourceGroupGap = Math.max(0f, drawing.number(tableConfig, "sourceGroupGap", 0f));
        // fillPage：明细页尽量画到页底，只在"这一页就是最后一页"时预留汇总/条款高度，
        // 避免非末页底部固定空出一大块（客户对账单这类连续表的诉求）。
        boolean fillPage = drawing.bool(tableConfig, "fillPage", false);
        float footerReserve = fillPage
                ? footerReserveHeight(root.path("summary"), root.path("clauses"), tableConfig)
                : 0f;
        // inline 分组跨页时，新页首行要补回上一行的分组值（序号/进货日期），否则续页的行看不出属于哪一天
        List<String> continuationFillFields = textList(tableConfig.path("grouping").path("continuationFillFields"));
        Map<String, String> lastGroupValues = new HashMap<>();
        // mergeColumns：连续多行的同值只画一个跨行单元格（序号/进货日期这类分组列）
        Set<String> mergeColumns = mergeColumnKeys(tableConfig.path("mergeColumns"));

        int itemIndex = 0;
        boolean firstPage = true;
        do {
            PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
            if (firstPage || repeatHeader) {
                pageContentRenderer.drawStatic(canvas, font, root.path("static"), variables, pageMetrics);
                pageContentRenderer.drawFields(canvas, fieldsConfig, data, font, pageMetrics);
            }
            boolean renderHeader = renderTable
                    && (repeatHeaderPerPage || (firstPage && !repeatHeaderPerSourceGroup));
            float currentTableTop = firstPage ? tableTop : continuationTableTop;
            float rowTop = currentTableTop + (renderHeader ? headerHeight : 0);
            int rowsOnPage = 0;
            boolean pageFirstDetailRow = true;
            float pageBottomLimit = tableBottom;
            if (fillPage) {
                float pageLimit = pageMetrics.height() - 14f;
                int remainingRows = 0;
                for (int index = itemIndex; index < items.size(); index++) {
                    Map<String, String> pending = items.get(index);
                    if (!groupPaginator.isBlankRow(pending)
                            && !groupPaginator.isGroupHeader(pending)
                            && !groupPaginator.isSubtotalRow(pending)) {
                        remainingRows++;
                    }
                }
                pageBottomLimit = pageLimit;
                if (remainingRows * rowHeight + footerReserve <= pageLimit - rowTop) {
                    pageBottomLimit = pageLimit - footerReserve;
                }
            }
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
                    if (!detailHeaderAfterProject && !repeatHeaderPerPage) {
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
                            pageBottomLimit,
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
                        pageBottomLimit,
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
                boolean subtotal = groupPaginator.isSubtotalRow(item);
                float itemHeight = subtotal
                        ? groupPaginator.subtotalHeight(tableConfig)
                        : groupHeader
                        ? groupPaginator.groupHeaderHeight(tableConfig, item)
                        : rowHeight;
                float followingHeaderHeight = detailHeaderAfterProject
                        && groupPaginator.isProjectGroupHeader(item) ? headerHeight : 0f;
                float followingSubtotalHeight = followingSubtotalHeight(items, itemIndex, tableConfig);
                if (rowsOnPage >= maxRowsPerPage
                        || rowTop + itemHeight + followingHeaderHeight + followingSubtotalHeight > pageBottomLimit) {
                    lastPage = false;
                    break;
                }
                if (repeatHeaderPerSourceGroup
                        && !repeatHeaderPerPage
                        && !detailHeaderAfterProject
                        && groupPaginator.isSourceGroupHeader(item)) {
                    tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                    rowTop += headerHeight;
                }
                Map<String, String> renderItem = item;
                if (pageFirstDetailRow && !groupHeader && !subtotal) {
                    renderItem = new LinkedHashMap<>(item);
                    for (String field : continuationFillFields) {
                        if (renderItem.getOrDefault(field, "").isBlank()) {
                            renderItem.put(field, lastGroupValues.getOrDefault(field, ""));
                        }
                    }
                }
                Map<String, Integer> mergeSpans = mergeSpans(
                        renderItem, items, itemIndex, mergeColumns, rowHeight, pageBottomLimit - rowTop);
                tableRenderer.drawItemRow(canvas, font, tableConfig, columns, rowTop, renderItem,
                        mergeSpans, mergeColumns, pageMetrics);
                rowTop += itemHeight;
                if (detailHeaderAfterProject && groupPaginator.isProjectGroupHeader(item)) {
                    tableRenderer.drawHeader(canvas, font, tableConfig, columns, rowTop, pageMetrics);
                    rowTop += headerHeight;
                }
                if (!groupHeader && !subtotal) {
                    for (String field : continuationFillFields) {
                        String value = item.get(field);
                        if (value != null && !value.isBlank()) {
                            lastGroupValues.put(field, value);
                        }
                    }
                    pageFirstDetailRow = false;
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

    /**
     * 当前行之后若紧跟小计行，返回其高度；用于明细行绘制前预判，
     * 保证小计行与本组最后一条明细分页时不被拆开。
     */
    private Set<String> mergeColumnKeys(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String key = entry.asText("").trim();
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private Map<String, Integer> mergeSpans(
            Map<String, String> current,
            List<Map<String, String>> items,
            int index,
            Set<String> mergeColumns,
            float rowHeight,
            float availableHeight
    ) {
        if (mergeColumns.isEmpty() || rowHeight <= 0f) {
            return Map.of();
        }
        int maxRows = Math.max(1, (int) (availableHeight / rowHeight));
        Map<String, Integer> spans = new LinkedHashMap<>();
        for (String key : mergeColumns) {
            String value = current.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            int span = 1;
            for (int next = index + 1; next < items.size() && span < maxRows; next++) {
                String nextValue = items.get(next).get(key);
                if (nextValue == null || nextValue.isBlank()) {
                    span++;
                } else {
                    break;
                }
            }
            spans.put(key, span);
        }
        return spans;
    }

    private float footerReserveHeight(JsonNode summary, JsonNode clauses, JsonNode tableConfig) {
        float reserve = 0f;
        if (summary != null && summary.isObject()) {
            int summaryLines = summary.path("lines").size();
            if (summaryLines == 0
                    && (summary.path("template").isTextual()
                    || summary.path("alignToColumns").asBoolean(false))) {
                summaryLines = 1;
            }
            if (summaryLines > 0) {
                reserve += summaryLines * drawing.number(
                        summary, "height", drawing.number(tableConfig, "rowHeight", 26f));
            }
            JsonNode grid = summary.path("grid");
            if (grid.isObject()) {
                reserve += grid.path("rows").size() * drawing.number(grid, "rowHeight", 24f);
            }
        }
        if (clauses != null && clauses.isObject()
                && drawing.childTextValues(clauses.path("lines")).size() > 0) {
            reserve += drawing.number(clauses, "paddingTop", 8f)
                    + drawing.number(clauses, "height", 96f);
        }
        return reserve <= 0f ? 0f : reserve + 6f;
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode entry : node) {
            String value = entry.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private float followingSubtotalHeight(
            List<Map<String, String>> items,
            int itemIndex,
            JsonNode tableConfig
    ) {
        int next = itemIndex + 1;
        if (next >= items.size() || !groupPaginator.isSubtotalRow(items.get(next))) {
            return 0f;
        }
        return groupPaginator.subtotalHeight(tableConfig);
    }

}
