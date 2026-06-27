package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
class PrintRecordEnricher {

    private static final String TYPE_DATA_LOOKUP = "dataLookup";
    private static final String TYPE_ITEM_LOOKUP_BY_FIELD = "itemLookupByField";
    private static final String RESULT_LIST = "list";

    private final JdbcTemplate jdbc;
    private final PrintRecordFieldFormatter formatter;
    private final PrintRuntimeProperties runtimeProperties;

    PrintRecordEnricher(JdbcTemplate jdbc, PrintRecordFieldFormatter formatter, PrintRuntimeProperties runtimeProperties) {
        this.jdbc = jdbc;
        this.formatter = formatter;
        this.runtimeProperties = runtimeProperties;
    }

    void enrich(String moduleKey, Map<String, String> data, List<Map<String, String>> items) {
        for (JsonNode rule : runtimeProperties.childObjects(runtimeProperties.enrichers(moduleKey))) {
            String type = runtimeProperties.text(rule, "type", "");
            try {
                if (TYPE_DATA_LOOKUP.equals(type)) {
                    applyDataLookup(data, rule);
                } else if (TYPE_ITEM_LOOKUP_BY_FIELD.equals(type)) {
                    applyItemLookup(data, items, rule);
                }
            } catch (RuntimeException ex) {
                log.debug("打印字段补充失败, moduleKey={}, type={}", moduleKey, type, ex);
            }
        }
    }

    private void applyDataLookup(Map<String, String> data, JsonNode rule) {
        String targetField = runtimeProperties.text(rule, "targetField", "");
        if (targetField.isBlank()) {
            return;
        }
        if (runtimeProperties.bool(rule, "stopWhenPresent", false) && !formatter.value(data, targetField).isBlank()) {
            return;
        }
        Object argument = argument(data, rule);
        if (argument == null) {
            return;
        }

        String sql = runtimeProperties.text(rule, "sql", "");
        if (RESULT_LIST.equals(runtimeProperties.text(rule, "result", ""))) {
            List<String> values = jdbc.queryForList(sql, String.class, argument);
            if (!values.isEmpty()) {
                data.put(targetField, String.join(runtimeProperties.text(rule, "join", ""), values));
            }
            return;
        }

        List<String> values = jdbc.queryForList(sql, String.class, argument);
        if (!values.isEmpty() && !values.get(0).isBlank()) {
            data.put(targetField, values.get(0));
        }
    }

    private Object argument(Map<String, String> data, JsonNode rule) {
        String sourceField = runtimeProperties.text(rule, "sourceField", "");
        String rawValue = formatter.value(data, sourceField).trim();
        if (rawValue.isEmpty()) {
            return null;
        }
        if ("long".equals(runtimeProperties.text(rule, "argumentType", ""))) {
            try {
                return Long.valueOf(rawValue);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return rawValue;
    }

    private void applyItemLookup(Map<String, String> data, List<Map<String, String>> items, JsonNode rule) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String sourceField = runtimeProperties.text(rule, "sourceField", "");
        Set<String> sourceValues = sourceValues(items, sourceField);
        if (sourceValues.isEmpty()) {
            return;
        }

        String sql = placeholders(runtimeProperties.text(rule, "sql", ""), sourceValues.size());
        List<Map<String, Object>> rows = jdbc.queryForList(sql, sourceValues.toArray());
        Map<String, Map<String, Object>> rowsByKey = rowsByKey(rows, runtimeProperties.text(rule, "lookupKeyColumn", ""));
        Map<String, JsonNode> targetFields = runtimeProperties.childFields(rule.path("targetFields"));
        Map<String, JsonNode> fallbackFields = runtimeProperties.childFields(rule.path("fallbackFields"));
        for (Map<String, String> item : items) {
            Map<String, Object> row = rowsByKey.get(formatter.value(item, sourceField));
            if (row == null) {
                applyFallbacks(data, item, fallbackFields);
                continue;
            }
            applyTargetFields(item, row, targetFields);
        }
    }

    private Set<String> sourceValues(List<Map<String, String>> items, String sourceField) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, String> item : items) {
            String value = formatter.value(item, sourceField).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String placeholders(String sql, int size) {
        if (size <= 0) {
            return sql;
        }
        return sql.replace(":values", String.join(",", java.util.Collections.nCopies(size, "?")));
    }

    private Map<String, Map<String, Object>> rowsByKey(List<Map<String, Object>> rows, String lookupKeyColumn) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (lookupKeyColumn.isBlank()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object key = row.get(lookupKeyColumn);
            if (key != null) {
                result.put(formatter.stringValue(key), row);
            }
        }
        return result;
    }

    private void applyTargetFields(Map<String, String> item, Map<String, Object> row, Map<String, JsonNode> targetFields) {
        for (Map.Entry<String, JsonNode> entry : targetFields.entrySet()) {
            Object value = row.get(entry.getValue().asText(""));
            if (value != null) {
                item.put(entry.getKey(), formatter.stringValue(value));
            }
        }
    }

    private void applyFallbacks(Map<String, String> data, Map<String, String> item, Map<String, JsonNode> fallbackFields) {
        for (Map.Entry<String, JsonNode> entry : fallbackFields.entrySet()) {
            String value = formatter.value(data, entry.getValue().asText(""));
            if (!value.isBlank()) {
                item.put(entry.getKey(), value);
            }
        }
    }
}
