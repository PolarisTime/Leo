package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrintLayoutLodopRenderer {

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final ObjectMapper objectMapper;

    public PrintLayoutLodopRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean supports(String templateHtml) {
        if (templateHtml == null || !templateHtml.trim().startsWith("{")) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(templateHtml);
            return root != null && root.isObject() && root.path("table").isObject();
        } catch (IOException ignored) {
            return false;
        }
    }

    public String render(String templateName, String templateHtml, Map<String, String> data, List<Map<String, String>> items) {
        try {
            JsonNode root = objectMapper.readTree(templateHtml);
            Map<String, String> variables = new HashMap<>(data);
            Totals totals = totals(items);
            variables.put("totalQuantity", formatQuantity(totals.quantity()));
            variables.put("totalWeight", formatDecimal(totals.weight(), 3));

            StringBuilder script = new StringBuilder();
            script.append("LODOP.PRINT_INIT(").append(jsString(templateName)).append(");\n");
            renderFields(script, root.path("fields"), data);
            renderTable(script, root.path("table"), items);
            float nextTop = nextTop(root.path("table"), items.size());
            nextTop = renderSummary(script, root.path("summary"), root.path("table"), variables, nextTop);
            renderClauses(script, root.path("clauses"), root.path("table"), nextTop);
            return script.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "套打布局配置不是合法 JSON");
        }
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

    private void renderTable(StringBuilder script, JsonNode table, List<Map<String, String>> items) {
        List<JsonNode> columns = childObjects(table.path("columns"));
        float left = number(table, "left", 28);
        float top = number(table, "top", 176);
        float headerHeight = number(table, "headerHeight", 28);
        float rowHeight = number(table, "rowHeight", 26);

        float x = left;
        for (JsonNode column : columns) {
            float width = number(column, "width", 60);
            addRect(script, top, x, width, headerHeight);
            addText(script, top + 8, x + 2, width - 4, 12, text(column, "label", ""), integer(column, "headerFontSize", 9));
            x += width;
        }

        for (int row = 0; row < items.size(); row++) {
            x = left;
            float rowTop = top + headerHeight + row * rowHeight;
            for (JsonNode column : columns) {
                float width = number(column, "width", 60);
                addRect(script, rowTop, x, width, rowHeight);
                addText(script, rowTop + 7, x + 2, width - 4, 12, itemValue(items.get(row), column), integer(column, "fontSize", 8));
                x += width;
            }
        }
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
                + number(table, "headerHeight", 28)
                + itemCount * number(table, "rowHeight", 26);
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
        if (template == null || template.isEmpty()) {
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
        if ("remark".equals(key) && (value == null || value.isBlank())) {
            return "无";
        }
        return value == null ? "" : value;
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

    private record Totals(BigDecimal quantity, BigDecimal weight) {
    }
}
