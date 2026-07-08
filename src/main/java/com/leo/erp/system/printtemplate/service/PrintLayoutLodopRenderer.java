package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrintLayoutLodopRenderer {

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    private static final float DEFAULT_TABLE_TOP = 176f;
    private static final float DEFAULT_PAGE_HEIGHT = 842f;
    private static final float DEFAULT_BOTTOM_MARGIN = 36f;

    private final ObjectMapper objectMapper;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintLayoutLodopRenderer(ObjectMapper objectMapper, PrintRuntimeProperties runtimeProperties) {
        this.objectMapper = objectMapper;
        this.runtimeProperties = runtimeProperties;
    }

    public boolean supports(String templateHtml) {
        if (templateHtml == null || !templateHtml.trim().startsWith("{")) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(templateHtml);
            return root != null && (root.path("table").isObject() || hasTables(root.path("tables")));
        } catch (IOException ignored) {
            return false;
        }
    }

    public String render(String templateName, String templateHtml, Map<String, String> data, List<Map<String, String>> items) {
        Map<String, List<Map<String, String>>> sections = new LinkedHashMap<>();
        sections.put(PrintRecordData.ITEMS_SECTION, items == null ? List.of() : items);
        return render(templateName, templateHtml, data, sections);
    }

    public String render(
            String templateName,
            String templateHtml,
            Map<String, String> data,
            Map<String, List<Map<String, String>>> sections
    ) {
        try {
            JsonNode root = objectMapper.readTree(templateHtml);
            Map<String, String> variables = summaryVariables(data, sections);

            StringBuilder script = new StringBuilder();
            script.append("LODOP.PRINT_INIT(").append(jsString(templateName)).append(");\n");
            renderFields(script, root.path("fields"), data);
            TableRenderResult tableResult = renderTables(script, root, data, sections);
            if (tableResult.table() != null) {
                float nextTop = tableResult.nextTop();
                float trailingHeight = summaryHeight(root.path("summary"), tableResult.table()) + clausesHeight(root.path("clauses"));
                if (trailingHeight > 0f && !fits(nextTop, trailingHeight, contentBottom(root))) {
                    addNewPage(script, root, data);
                    nextTop = number(root.path("summary"), "top", resetTop(tableResult.table()));
                }
                nextTop = renderSummary(script, root.path("summary"), tableResult.table(), variables, nextTop);
                renderClauses(script, root.path("clauses"), tableResult.table(), nextTop);
            }
            return script.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "套打布局配置不是合法 JSON");
        }
    }

    private Map<String, String> summaryVariables(Map<String, String> data, Map<String, List<Map<String, String>>> sections) {
        List<Map<String, String>> items = sections == null
                ? List.of()
                : sections.getOrDefault(PrintRecordData.ITEMS_SECTION, List.of());
        Map<String, String> variables = new HashMap<>(data);
        Totals totals = totals(items);
        variables.put("totalQuantity", formatQuantity(totals.quantity()));
        variables.put(
                "totalWeight",
                formatDecimal(totals.weight(), PrecisionConstants.DISPLAY_WEIGHT_SCALE)
        );
        PrintChargeSummary.applyTo(variables, sections);
        return variables;
    }

    private void renderFields(StringBuilder script, JsonNode fields, Map<String, String> data) {
        Iterator<Map.Entry<String, JsonNode>> iterator = fields.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            JsonNode field = entry.getValue();
            String value = formatValue(resolveValue(data, field.path("source"), entry.getKey()), text(field, "format", ""));
            if (value.isBlank() || !hasBox(field)) {
                continue;
            }
            addText(
                    script,
                    number(field, "top", 0),
                    number(field, "left", 0),
                    number(field, "width", 100),
                    number(field, "height", 20),
                    value,
                    integer(field, "fontSize", 9)
            );
        }
    }

    private float renderTablePage(StringBuilder script, JsonNode table, List<Map<String, String>> items, boolean drawHeader) {
        List<JsonNode> columns = childObjects(table.path("columns"));
        float left = number(table, "left", 28);
        float top = number(table, "top", 176);
        float headerHeight = number(table, "headerHeight", 28);
        float rowHeight = number(table, "rowHeight", 26);
        float rowTop = top;
        if (drawHeader) {
            String title = text(table, "title", "");
            if (!title.isBlank()) {
                float titleHeight = number(table, "titleHeight", 22);
                addRect(script, rowTop, left, tableWidth(table), titleHeight);
                addText(script, rowTop + 6, left + 4, tableWidth(table) - 8, 14, title, integer(table, "titleFontSize", 9));
                rowTop += titleHeight;
            }

            float x = left;
            for (JsonNode column : columns) {
                float width = number(column, "width", 60);
                addRect(script, rowTop, x, width, headerHeight);
                addText(script, rowTop + 8, x + 2, width - 4, 12, text(column, "label", ""), integer(column, "headerFontSize", 9));
                x += width;
            }
            rowTop += headerHeight;
        }

        if (items.isEmpty()) {
            addRect(script, rowTop, left, tableWidth(table), rowHeight);
            String emptyText = text(table, "emptyText", "");
            if (!emptyText.isBlank()) {
                addText(script, rowTop + 7, left, tableWidth(table), 12, emptyText, integer(table, "emptyFontSize", 8));
            }
            return rowTop + rowHeight;
        }

        for (int row = 0; row < items.size(); row++) {
            float x = left;
            float currentTop = rowTop + row * rowHeight;
            for (JsonNode column : columns) {
                float width = number(column, "width", 60);
                addRect(script, currentTop, x, width, rowHeight);
                addText(script, currentTop + 7, x + 2, width - 4, 12, itemValue(items.get(row), column), integer(column, "fontSize", 8));
                x += width;
            }
        }
        return rowTop + items.size() * rowHeight;
    }

    private TableRenderResult renderTables(
            StringBuilder script,
            JsonNode root,
            Map<String, String> data,
            Map<String, List<Map<String, String>>> sections
    ) {
        JsonNode lastTable = null;
        float nextTop = 0f;
        float contentBottom = contentBottom(root);
        for (JsonNode table : tableConfigs(root)) {
            List<Map<String, String>> rows = rows(table, sections);
            if (rows.isEmpty() && !bool(table, "emptyVisible", true)) {
                continue;
            }
            float tableTop = table.has("top")
                    ? number(table, "top", 176)
                    : (nextTop <= 0f ? 176 : nextTop + number(table, "marginTop", 8));
            JsonNode activeTable = tableAtTop(table, tableTop);
            float rowHeight = number(activeTable, "rowHeight", 26);
            int maxRowsPerPage = Math.max(1, integer(activeTable, "maxRowsPerPage", 16));

            if (rows.isEmpty()) {
                if (!fits(tableTop, titleHeight(activeTable) + number(activeTable, "headerHeight", 28) + rowHeight, contentBottom)) {
                    addNewPage(script, root, data);
                    activeTable = tableAtTop(table, resetTop(table));
                }
                nextTop = renderTablePage(script, activeTable, rows, true);
                lastTable = activeTable;
                continue;
            }

            int rowIndex = 0;
            while (rowIndex < rows.size()) {
                boolean drawHeader = rowIndex == 0 || bool(activeTable, "repeatHeader", true);
                float headerBlockHeight = drawHeader ? titleHeight(activeTable) + number(activeTable, "headerHeight", 28) : 0f;
                if (!fits(number(activeTable, "top", DEFAULT_TABLE_TOP), headerBlockHeight + rowHeight, contentBottom)) {
                    addNewPage(script, root, data);
                    activeTable = tableAtTop(table, resetTop(table));
                    drawHeader = true;
                    headerBlockHeight = titleHeight(activeTable) + number(activeTable, "headerHeight", 28);
                }
                int rowsOnPage = rowsOnPage(
                        rows.size() - rowIndex,
                        maxRowsPerPage,
                        rowHeight,
                        number(activeTable, "top", DEFAULT_TABLE_TOP),
                        headerBlockHeight,
                        contentBottom
                );
                nextTop = renderTablePage(script, activeTable, rows.subList(rowIndex, rowIndex + rowsOnPage), drawHeader);
                rowIndex += rowsOnPage;
                lastTable = activeTable;
                if (rowIndex < rows.size()) {
                    addNewPage(script, root, data);
                    activeTable = tableAtTop(table, resetTop(table));
                }
            }
        }
        return new TableRenderResult(lastTable, nextTop);
    }

    private float renderSummary(
            StringBuilder script,
            JsonNode summary,
            JsonNode table,
            Map<String, String> variables,
            float top
    ) {
        if (!summary.isObject()) {
            return top;
        }
        float left = number(table, "left", 28);
        float width = tableWidth(table);
        float height = number(summary, "height", number(table, "rowHeight", 26));
        if (bool(summary, "border", true)) {
            addRect(script, top, left, width, height);
        }
        addText(
                script,
                top + number(summary, "paddingTop", 7),
                left + number(summary, "paddingLeft", 6),
                width - number(summary, "paddingLeft", 6) * 2,
                12,
                applyTemplate(text(summary, "template", ""), variables),
                integer(summary, "fontSize", 9)
        );
        return top + height;
    }

    private void renderClauses(StringBuilder script, JsonNode clauses, JsonNode table, float top) {
        if (!clauses.isObject()) {
            return;
        }
        float left = number(clauses, "left", number(table, "left", 28));
        float width = number(clauses, "width", tableWidth(table));
        float lineTop = top + number(clauses, "paddingTop", 8);
        float lineHeight = number(clauses, "lineHeightPx", 14);
        for (String line : childTextValues(clauses.path("lines"))) {
            addText(script, lineTop, left, width, lineHeight, line, integer(clauses, "fontSize", 8));
            lineTop += lineHeight;
        }
    }

    private float nextTop(JsonNode table, int itemCount) {
        return number(table, "top", 176)
                + titleHeight(table)
                + number(table, "headerHeight", 28)
                + itemCount * number(table, "rowHeight", 26);
    }

    private float titleHeight(JsonNode table) {
        return text(table, "title", "").isBlank() ? 0 : number(table, "titleHeight", 22);
    }

    private int rowsOnPage(int remainingRows, int maxRowsPerPage, float rowHeight, float tableTop, float headerBlockHeight, float contentBottom) {
        float availableHeight = Math.max(rowHeight, contentBottom - tableTop - headerBlockHeight);
        int rowsByHeight = Math.max(1, (int) Math.floor(availableHeight / rowHeight));
        return Math.min(remainingRows, Math.min(maxRowsPerPage, rowsByHeight));
    }

    private boolean fits(float top, float height, float contentBottom) {
        return top + height <= contentBottom;
    }

    private float contentBottom(JsonNode root) {
        JsonNode page = root.path("page");
        return number(page, "height", DEFAULT_PAGE_HEIGHT) - number(page, "bottomMargin", DEFAULT_BOTTOM_MARGIN);
    }

    private float resetTop(JsonNode table) {
        return number(table, "pageResetTop", number(table, "top", DEFAULT_TABLE_TOP));
    }

    private float summaryHeight(JsonNode summary, JsonNode table) {
        if (!summary.isObject()) {
            return 0f;
        }
        return number(summary, "height", number(table, "rowHeight", 26));
    }

    private float clausesHeight(JsonNode clauses) {
        if (!clauses.isObject() || childTextValues(clauses.path("lines")).isEmpty()) {
            return 0f;
        }
        return number(clauses, "paddingTop", 8) + number(clauses, "height", 96);
    }

    private void addNewPage(StringBuilder script, JsonNode root, Map<String, String> data) {
        script.append("LODOP.NewPage();\n");
        renderFields(script, root.path("fields"), data);
    }

    private List<JsonNode> tableConfigs(JsonNode root) {
        List<JsonNode> tables = childObjects(root.path("tables"));
        if (!tables.isEmpty()) {
            return tables;
        }
        JsonNode table = root.path("table");
        return table.isObject() ? List.of(table) : List.of();
    }

    private List<Map<String, String>> rows(JsonNode table, Map<String, List<Map<String, String>>> sections) {
        if (sections == null) {
            return List.of();
        }
        return sections.getOrDefault(text(table, "source", PrintRecordData.ITEMS_SECTION), List.of());
    }

    private JsonNode tableAtTop(JsonNode table, float top) {
        if (!table.isObject() || table.has("top")) {
            return table;
        }
        ObjectNode copy = table.deepCopy();
        copy.put("top", top);
        return copy;
    }

    private boolean hasTables(JsonNode tables) {
        return !childObjects(tables).isEmpty();
    }

    private void addRect(StringBuilder script, float top, float left, float width, float height) {
        script.append("LODOP.ADD_PRINT_RECT(")
                .append(round(top)).append(',')
                .append(round(left)).append(',')
                .append(round(width)).append(',')
                .append(round(height)).append(",0,1);\n");
    }

    private void addText(StringBuilder script, float top, float left, float width, float height, String value, int fontSize) {
        script.append("LODOP.SET_PRINT_STYLE(\"FontSize\",").append(fontSize).append(");\n");
        script.append("LODOP.ADD_PRINT_TEXT(")
                .append(round(top)).append(',')
                .append(round(left)).append(',')
                .append(round(width)).append(',')
                .append(round(height)).append(',')
                .append(jsString(value)).append(");\n");
    }

    private boolean hasBox(JsonNode node) {
        return node.has("left") && node.has("top") && node.has("width") && node.has("height");
    }

    private String itemValue(Map<String, String> item, JsonNode column) {
        String value = resolveValue(item, column.path("source"), text(column, "key", ""));
        if ("compactAscii".equals(text(column, "normalize", ""))) {
            return value.replaceAll("(?<=[A-Za-z0-9])\\s+(?=[A-Za-z0-9])", "");
        }
        return value;
    }

    private String resolveValue(Map<String, String> data, JsonNode source, String fallbackKey) {
        if (source.isArray()) {
            for (JsonNode key : source) {
                String value = data.get(key.asText(""));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }
        String key = source.isTextual() ? source.asText() : fallbackKey;
        return data.getOrDefault(key, "");
    }

    private String formatValue(String value, String format) {
        if (!"chineseDate".equals(format) || value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String datePart = value.trim().split("[T\\s]", 2)[0];
        if (datePart.contains("年")) {
            return datePart;
        }
        String[] parts = datePart.split("[-/]");
        if (parts.length < 3) {
            return value;
        }
        String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
        String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
        return parts[0] + "年" + month + "月" + day + "日";
    }

    private Totals totals(List<Map<String, String>> items) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (Map<String, String> item : items) {
            quantity = quantity.add(decimal(item.get("quantity")));
            weight = weight.add(decimal(item.get("weightTon")));
        }
        return new Totals(quantity, weight);
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatDecimal(BigDecimal value, int scale) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? "0"
                : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String applyTemplate(String template, Map<String, String> variables) {
        if (template.isEmpty()) {
            return "";
        }
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(templateValue(key, variables.get(key))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String templateValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return runtimeProperties.templateDefault(key);
        }
        return value;
    }

    private float tableWidth(JsonNode table) {
        if (table.has("width")) {
            return number(table, "width", 539);
        }
        float width = 0;
        for (JsonNode column : childObjects(table.path("columns"))) {
            width += number(column, "width", 60);
        }
        return width;
    }

    private List<JsonNode> childObjects(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isObject()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private List<String> childTextValues(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.asText("").isBlank()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }

    private float number(JsonNode node, String field, float fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? (float) child.asDouble() : fallback;
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? child.asInt() : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : fallback;
    }

    private int round(float value) {
        return Math.round(value);
    }

    private String jsString(String value) {
        try {
            return objectMapper.writeValueAsString(value == null ? "" : value);
        } catch (IOException ex) {
            return "\"\"";
        }
    }

    private record TableRenderResult(JsonNode table, float nextTop) {
    }

    private record Totals(BigDecimal quantity, BigDecimal weight) {
    }
}
