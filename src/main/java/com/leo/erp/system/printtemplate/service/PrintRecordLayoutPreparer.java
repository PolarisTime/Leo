package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
class PrintRecordLayoutPreparer {

    private record CoordLayout(
            int tableTop,
            int rowHeight,
            int maxRows,
            int pageHeight,
            Integer sumTop,
            boolean limitToMaxRows,
            Integer pageBreakRows,
            int pageResetTop,
            String groupByField,
            String separatorField
    ) {
        private boolean shouldStartNewPage(int rowsOnPage, int nextRowTop) {
            if (pageBreakRows != null && rowsOnPage >= pageBreakRows) {
                return true;
            }
            return pageHeight > 0 && nextRowTop + rowHeight * 2 > pageHeight;
        }

        private boolean groupingEnabled() {
            return !groupByField.isBlank() && !separatorField.isBlank();
        }
    }

    private final PrintRecordFieldFormatter formatter;
    private final PrintRuntimeProperties runtimeProperties;

    PrintRecordLayoutPreparer(PrintRecordFieldFormatter formatter, PrintRuntimeProperties runtimeProperties) {
        this.formatter = formatter;
        this.runtimeProperties = runtimeProperties;
    }

    List<Map<String, String>> prepare(
            String moduleKey,
            String templateName,
            String templateHtml,
            Map<String, String> data,
            List<Map<String, String>> rawItems
    ) {
        enrichTopLevelPrintFields(data);
        CoordLayout layout = resolveCoordLayout(moduleKey, templateName);
        boolean needsLayout = templateHtml != null && layoutFieldPattern().matcher(templateHtml).find();
        boolean needsGrouping = needsLayout && layout.groupingEnabled();
        List<Map<String, String>> items = needsLayout
                ? flattenItemsForLayout(layout, rawItems)
                : rawItems.stream().map(formatter::enrichItemPrintFields).toList();
        if (needsLayout && layout.limitToMaxRows() && !needsGrouping) {
            items = items.stream().limit(layout.maxRows()).toList();
        }

        return prepareRows(data, items, layout, needsLayout, needsGrouping);
    }

    private List<Map<String, String>> prepareRows(
            Map<String, String> data,
            List<Map<String, String>> items,
            CoordLayout layout,
            boolean needsLayout,
            boolean needsGrouping
    ) {
        Map<String, BigDecimal> totals = initialTotals();
        int rowTop = layout.tableTop();
        int rowsOnPage = 0;
        int index = 1;
        String separatorSourceField = runtimeProperties.text(runtimeProperties.topLevelFields(), "separatorSourceField", "");
        String previousSeparatorValue = null;
        List<Map<String, String>> result = new ArrayList<>();

        for (Map<String, String> item : items) {
            Map<String, String> enriched = new HashMap<>(item);
            boolean separator = "true".equals(enriched.get("isSeparator"));
            if (!separator) {
                enriched.put("index", String.valueOf(index++));
                if (needsLayout && layout.shouldStartNewPage(rowsOnPage, rowTop)) {
                    enriched.put("needsNewPage", "true");
                    rowTop = layout.pageResetTop();
                    rowsOnPage = 0;
                }
                String separatorValue = value(enriched, separatorSourceField);
                if (needsLayout
                        && previousSeparatorValue != null
                        && !separatorValue.isBlank()
                        && !separatorValue.equals(previousSeparatorValue)) {
                    enriched.put("needsSeparator", "true");
                }
                previousSeparatorValue = separatorValue;
                addTotals(totals, enriched);
            }
            if (needsLayout) {
                enriched.put("rowTop", String.valueOf(rowTop));
                rowTop += layout.rowHeight();
                if (!separator) {
                    rowsOnPage += 1;
                }
            }
            result.add(enriched);
        }

        applyTotals(data, result, totals);
        if (needsLayout) {
            enrichLayoutFields(data, result, layout, needsGrouping);
        }
        return result;
    }

    private Pattern layoutFieldPattern() {
        List<String> fields = runtimeProperties.childTextValues(runtimeProperties.legacyLayout().path("dynamicFields"));
        if (fields.isEmpty()) {
            return Pattern.compile("a^");
        }
        String alternatives = fields.stream().map(Pattern::quote).reduce((left, right) -> left + "|" + right).orElse("");
        return Pattern.compile("\\{\\{\\s*(?:#if\\s+)?(?:" + alternatives + ")\\s*\\}\\}");
    }

    private CoordLayout resolveCoordLayout(String moduleKey, String templateName) {
        JsonNode legacyLayout = runtimeProperties.legacyLayout();
        for (JsonNode rule : runtimeProperties.childObjects(legacyLayout.path("rules"))) {
            if (matches(rule.path("when"), moduleKey, templateName)) {
                return toLayout(rule.path("layout"));
            }
        }
        return toLayout(legacyLayout.path("defaultLayout"));
    }

    private boolean matches(JsonNode condition, String moduleKey, String templateName) {
        String module = runtimeProperties.text(condition, "module", "");
        if (!module.isBlank() && !module.equals(moduleKey)) {
            return false;
        }
        List<String> moduleIn = runtimeProperties.childTextValues(condition.path("moduleIn"));
        if (!moduleIn.isEmpty() && !moduleIn.contains(moduleKey)) {
            return false;
        }
        String contains = runtimeProperties.text(condition, "templateNameContains", "");
        if (!contains.isBlank() && (templateName == null || !templateName.contains(contains))) {
            return false;
        }
        for (String value : runtimeProperties.childTextValues(condition.path("templateNameContainsAll"))) {
            if (templateName == null || !templateName.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private CoordLayout toLayout(JsonNode node) {
        return new CoordLayout(
                runtimeProperties.integer(node, "tableTop", 130),
                runtimeProperties.integer(node, "rowHeight", 20),
                runtimeProperties.integer(node, "maxRows", 50),
                runtimeProperties.integer(node, "pageHeight", 0),
                node.has("sumTop") ? node.path("sumTop").asInt() : null,
                runtimeProperties.bool(node, "limitToMaxRows", false),
                node.has("pageBreakRows") ? node.path("pageBreakRows").asInt() : null,
                runtimeProperties.integer(node, "pageResetTop", 20),
                runtimeProperties.text(node, "groupByField", ""),
                runtimeProperties.text(node, "separatorField", "")
        );
    }

    private void enrichTopLevelPrintFields(Map<String, String> data) {
        LocalDateTime now = LocalDateTime.now();
        data.putIfAbsent("printDate", now.toLocalDate().toString());
        data.putIfAbsent("printTime", now.toString());
        JsonNode topLevel = runtimeProperties.topLevelFields();
        putDateParts(data, firstPresent(data, runtimeProperties.childTextValues(topLevel.path("dateParts").path("sourceKeys"))));
        for (JsonNode field : runtimeProperties.childObjects(topLevel.path("decimalFields"))) {
            formatter.formatDecimalField(
                    data,
                    runtimeProperties.text(field, "field", ""),
                    runtimeProperties.scale(field, "scale", PrintRecordFieldFormatter.PRICE_SCALE)
            );
        }
        JsonNode headerBusinessNo = topLevel.path("headerBusinessNo");
        String target = runtimeProperties.text(headerBusinessNo, "target", "");
        if (!target.isBlank()) {
            data.putIfAbsent(target, firstPresent(data, runtimeProperties.childTextValues(headerBusinessNo.path("sourceKeys"))));
        }
        enrichAdaptiveFields(data, topLevel.path("adaptiveFields"));
    }

    private void enrichAdaptiveFields(Map<String, String> data, JsonNode adaptiveFields) {
        for (JsonNode field : runtimeProperties.childObjects(adaptiveFields)) {
            int width = displayWidth(firstPresent(data, List.of(runtimeProperties.text(field, "sourceField", ""))));
            boolean matched = false;
            for (JsonNode rule : runtimeProperties.childObjects(field.path("rules"))) {
                if (width > runtimeProperties.integer(rule, "minDisplayWidth", Integer.MAX_VALUE)) {
                    putValues(data, rule.path("values"));
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                putValues(data, field.path("defaultValues"));
            }
        }
    }

    private void putValues(Map<String, String> data, JsonNode values) {
        runtimeProperties.childFields(values).forEach((key, value) -> data.put(key, value.asText("")));
    }

    private void putDateParts(Map<String, String> data, String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return;
        }
        String[] parts = rawDate.split("[T\\s]", 2)[0].split("[-/年/月/日]");
        if (parts.length < 3) {
            return;
        }
        JsonNode targets = runtimeProperties.topLevelFields().path("dateParts").path("targets");
        data.put(runtimeProperties.text(targets, "year", "dateYear"), parts[0]);
        data.put(runtimeProperties.text(targets, "month", "dateMonth"), parts[1].length() == 1 ? "0" + parts[1] : parts[1]);
        data.put(runtimeProperties.text(targets, "day", "dateDay"), parts[2].length() == 1 ? "0" + parts[2] : parts[2]);
    }

    private Map<String, BigDecimal> initialTotals() {
        Map<String, BigDecimal> totals = new HashMap<>();
        runtimeProperties.childFields(runtimeProperties.totals()).keySet().forEach(key -> totals.put(key, BigDecimal.ZERO));
        return totals;
    }

    private void addTotals(Map<String, BigDecimal> totals, Map<String, String> item) {
        for (Map.Entry<String, JsonNode> entry : runtimeProperties.childFields(runtimeProperties.totals()).entrySet()) {
            String itemField = runtimeProperties.text(entry.getValue(), "itemField", "");
            totals.computeIfPresent(entry.getKey(), (key, total) -> total.add(formatter.decimal(value(item, itemField))));
        }
    }

    private void applyTotals(Map<String, String> data, List<Map<String, String>> items, Map<String, BigDecimal> totals) {
        Map<String, String> formattedTotals = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : runtimeProperties.childFields(runtimeProperties.totals()).entrySet()) {
            JsonNode config = entry.getValue();
            String outputField = runtimeProperties.text(config, "outputField", "");
            if (outputField.isBlank()) {
                continue;
            }
            String format = runtimeProperties.formatName(config, "format", "");
            String value = formatTotal(totals.getOrDefault(entry.getKey(), BigDecimal.ZERO), format);
            formattedTotals.put(outputField, value);
            data.put(outputField, value);
        }
        for (Map<String, String> item : items) {
            item.putAll(formattedTotals);
        }
    }

    private String formatTotal(BigDecimal value, String format) {
        if (runtimeProperties.isQuantityFormat(format)) {
            return formatter.formatQuantity(value);
        }
        return formatter.formatDecimal(value, runtimeProperties.scale(format, PrintRecordFieldFormatter.PRICE_SCALE));
    }

    private void enrichLayoutFields(
            Map<String, String> data,
            List<Map<String, String>> result,
            CoordLayout layout,
            boolean needsGrouping
    ) {
        long itemCount = result.stream().filter(item -> !"true".equals(item.get("isSeparator"))).count();
        int actualItemCount = needsGrouping ? result.size() : (int) Math.min(itemCount, layout.maxRows());
        int sumTop = layout.sumTop() != null ? layout.sumTop() : layout.tableTop() + actualItemCount * layout.rowHeight();
        if (!needsGrouping && layout.sumTop() == null) {
            sumTop = layout.tableTop() + layout.maxRows() * layout.rowHeight();
        }
        int footerTop = sumTop + 40;
        data.put("sumTop", String.valueOf(sumTop));
        data.put("sumTop2", String.valueOf(sumTop + 2));
        data.put("footerTop", String.valueOf(footerTop));
        data.put("footerLineTop", String.valueOf(footerTop + 28));
        data.put("footerDateTop", String.valueOf(footerTop + 34));
        data.put("hasEmptyRows", !needsGrouping && itemCount < layout.maxRows() ? "true" : "");
        data.put("emptyRowTop", String.valueOf(layout.tableTop() + itemCount * layout.rowHeight()));
    }

    private List<Map<String, String>> flattenItemsForLayout(CoordLayout layout, List<Map<String, String>> rawItems) {
        if (!layout.groupingEnabled() || rawItems.isEmpty()) {
            return rawItems.stream().map(formatter::enrichItemPrintFields).toList();
        }

        Map<String, List<Map<String, String>>> groupMap = new java.util.LinkedHashMap<>();
        for (Map<String, String> item : rawItems) {
            String groupValue = value(item, layout.groupByField());
            groupMap.computeIfAbsent(groupValue, key -> new ArrayList<>()).add(formatter.enrichItemPrintFields(item));
        }

        List<Map<String, String>> result = new ArrayList<>();
        groupMap.forEach((groupValue, groupItems) -> {
            result.addAll(groupItems);
            if (!groupValue.isBlank()) {
                Map<String, String> separator = new HashMap<>();
                separator.put("isSeparator", "true");
                separator.put(layout.separatorField(), groupValue);
                result.add(separator);
            }
        });
        return result;
    }

    private String firstPresent(Map<String, String> data, List<String> keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private int displayWidth(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            width += codePoint <= 0x00FF ? 1 : 2;
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private String value(Map<String, String> row, String key) {
        return formatter.value(row, key);
    }
}
