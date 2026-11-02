package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 PDF_FORM 表格配置生成通用分组行。
 * 业务字段、分组键、排序和汇总映射由模板 JSON 声明，渲染层只负责执行。
 */
@Component
public final class PrintPdfItemGrouper {

    private static final String GROUP_HEADER_KEY = "isGroupHeader";

    List<Map<String, String>> group(List<Map<String, String>> items, JsonNode grouping) {
        if (items == null || items.isEmpty() || grouping == null || !grouping.isObject()) {
            return items == null ? List.of() : items;
        }

        String groupBy = text(grouping, "by", "");
        if (groupBy.isBlank()) {
            return items;
        }

        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        for (Map<String, String> item : items) {
            String key = normalize(item.get(groupBy));
            groups.computeIfAbsent(key.isBlank() ? "__unassigned__" : key, ignored -> new ArrayList<>())
                    .add(item);
        }

        List<List<Map<String, String>>> orderedGroups = new ArrayList<>(groups.values());
        sortGroups(orderedGroups, grouping);

        List<Map<String, String>> result = new ArrayList<>(items.size() + orderedGroups.size() * 2);
        int groupIndex = 0;
        boolean separator = grouping.path("separator").asBoolean(false);
        boolean inline = grouping.path("inline").asBoolean(false);
        List<String> blankRepeatedFields = textList(grouping.path("blankRepeatedFields"));
        for (List<Map<String, String>> groupItems : orderedGroups) {
            if (!inline && separator && !result.isEmpty()) {
                result.add(Map.of("isBlankRow", "true"));
            }
            groupIndex++;
            if (!inline) {
                result.add(buildHeader(groupItems, grouping, groupIndex));
                for (Map<String, String> item : groupItems) {
                    Map<String, String> detail = new LinkedHashMap<>(item);
                    detail.put(indexField(grouping), String.valueOf(groupIndex));
                    result.add(detail);
                }
                continue;
            }
            /*
             * inline 分组：不插入"分组头行"，把序号写在组内第一行、组内其余行留空，
             * 视觉上等价于 Excel 的合并单元格（序号/日期/备注只出现一次），
             * 这样一张对账单就是一张连续明细表，不再被组头与小计切碎。
             */
            for (int index = 0; index < groupItems.size(); index++) {
                Map<String, String> detail = new LinkedHashMap<>(groupItems.get(index));
                detail.put(indexField(grouping), index == 0 ? String.valueOf(groupIndex) : "");
                if (index > 0) {
                    for (String field : blankRepeatedFields) {
                        detail.put(field, "");
                    }
                }
                result.add(detail);
            }
        }
        return result;
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

    private Map<String, String> buildHeader(
            List<Map<String, String>> items,
            JsonNode grouping,
            int groupIndex
    ) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put(GROUP_HEADER_KEY, text(grouping, "headerType", "source"));
        header.put(indexField(grouping), String.valueOf(groupIndex));

        JsonNode headerFields = grouping.path("headerFields");
        if (headerFields.isArray()) {
            for (JsonNode field : headerFields) {
                String key = field.asText("").trim();
                if (!key.isBlank()) {
                    header.put(key, normalize(items.get(0).get(key)));
                }
            }
        }

        JsonNode totals = grouping.path("totals");
        if (totals.isObject()) {
            totals.fields().forEachRemaining(entry -> {
                JsonNode config = entry.getValue();
                String source = text(config, "source", entry.getKey());
                int scale = Math.max(0, config.path("scale").asInt(2));
                header.put(entry.getKey(), format(sum(items, source), scale));
            });
        }
        return header;
    }

    private String indexField(JsonNode grouping) {
        return text(grouping, "indexField", "groupIndex");
    }

    private void sortGroups(List<List<Map<String, String>>> groups, JsonNode grouping) {
        String sortBy = text(grouping, "sortBy", "");
        if (sortBy.isBlank()) {
            return;
        }
        Comparator<List<Map<String, String>>> comparator = Comparator.comparing(
                group -> normalize(group.get(0).get(sortBy)),
                Comparator.nullsLast(String::compareTo)
        );
        if ("desc".equalsIgnoreCase(text(grouping, "sortDirection", "asc"))) {
            comparator = comparator.reversed();
        }
        groups.sort(comparator);
    }

    private BigDecimal sum(List<Map<String, String>> items, String field) {
        return items.stream()
                .map(item -> decimal(item.get(field)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private String format(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        JsonNode child = node.path(field);
        return child.isTextual() ? child.asText() : fallback;
    }
}
