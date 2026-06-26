package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.leo.erp.common.support.PrecisionConstants;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrintPdfFormValueResolver {

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    String fieldValue(Map<String, String> data, JsonNode fieldConfig, String fieldName) {
        String value = resolveValue(data, fieldConfig.path("source"), fieldName);
        return formatValue(value, text(fieldConfig, "format", ""));
    }

    String itemValue(Map<String, String> item, JsonNode column) {
        String value = resolveValue(item, column.path("source"), text(column, "key", ""));
        if ("compactAscii".equals(text(column, "normalize", ""))) {
            return compactAsciiToken(value);
        }
        return value;
    }

    Map<String, String> summaryVariables(Map<String, String> data, List<Map<String, String>> items) {
        Totals totals = totals(items);
        Map<String, String> variables = new HashMap<>(data);
        variables.put("totalQuantity", formatQuantity(totals.quantity()));
        variables.put(
                "totalWeight",
                formatDecimal(totals.weight(), PrecisionConstants.DISPLAY_WEIGHT_SCALE)
        );
        return variables;
    }

    String applyTemplate(String template, Map<String, String> variables) {
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
        if ("chineseDate".equals(format)) {
            return formatChineseDate(value);
        }
        return value;
    }

    private String formatChineseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "";
        }
        String datePart = rawDate.trim().split("[T\\s]", 2)[0];
        if (datePart.contains("年")) {
            return datePart;
        }
        String[] parts = datePart.split("[-/]");
        if (parts.length < 3) {
            return rawDate;
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

    private String templateValue(String key, String value) {
        if ("remark".equals(key) && (value == null || value.isBlank())) {
            return "无";
        }
        return value == null ? "" : value;
    }

    private String compactAsciiToken(String value) {
        return value == null ? "" : value.replaceAll("(?<=[A-Za-z0-9])\\s+(?=[A-Za-z0-9])", "");
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }

    private record Totals(BigDecimal quantity, BigDecimal weight) {
    }
}
