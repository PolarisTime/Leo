package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.leo.erp.common.support.PrecisionConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
class PrintRecordFieldFormatter {

    static final int WEIGHT_SCALE = PrecisionConstants.DISPLAY_WEIGHT_SCALE;
    static final int PRICE_SCALE = 2;

    private static final String PIECE_WEIGHT_FIELD = "pieceWeightTon";

    private final PrintRuntimeProperties runtimeProperties;

    PrintRecordFieldFormatter(PrintRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    Map<String, String> enrichItemPrintFields(Map<String, String> item) {
        Map<String, String> enriched = new HashMap<>(item);
        if (shouldSuppressPieceWeight(enriched)) {
            JsonNode pieceWeightConfig = runtimeProperties.pieceWeightConfig();
            enriched.put(PIECE_WEIGHT_FIELD, runtimeProperties.text(pieceWeightConfig, "replacement", "-"));
        } else if (value(enriched, PIECE_WEIGHT_FIELD).isBlank()) {
            BigDecimal quantity = decimal(value(enriched, "quantity"));
            BigDecimal weight = decimal(value(enriched, "weightTon"));
            if (quantity.compareTo(BigDecimal.ZERO) > 0 && weight.compareTo(BigDecimal.ZERO) > 0) {
                enriched.put(PIECE_WEIGHT_FIELD, weight.divide(quantity, WEIGHT_SCALE, RoundingMode.HALF_UP).toPlainString());
            }
        }
        formatDecimalField(enriched, PIECE_WEIGHT_FIELD, WEIGHT_SCALE);
        formatDecimalField(enriched, "weightTon", WEIGHT_SCALE);
        formatDecimalField(enriched, "unitPrice", PRICE_SCALE);
        formatDecimalField(enriched, "amount", PRICE_SCALE);
        return enriched;
    }

    void formatDecimalField(Map<String, String> row, String key, int scale) {
        String value = row.get(key);
        if (value == null || value.isBlank() || "-".equals(value)) {
            return;
        }
        BigDecimal decimal = decimal(value);
        if (decimal.compareTo(BigDecimal.ZERO) == 0) {
            row.put(key, "");
            return;
        }
        row.put(key, formatDecimal(decimal, scale));
    }

    BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    String formatDecimal(BigDecimal value, int scale) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    String formatQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? "" : value.stripTrailingZeros().toPlainString();
    }

    Map<String, String> toCamelStringMap(Map<String, ?> row) {
        Map<String, String> result = new HashMap<>();
        for (var entry : row.entrySet()) {
            if (entry.getValue() != null) {
                result.put(toCamelCase(entry.getKey()), stringValue(entry.getValue()));
            }
        }
        return result;
    }

    String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        return String.valueOf(value);
    }

    String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private boolean shouldSuppressPieceWeight(Map<String, String> item) {
        JsonNode config = runtimeProperties.pieceWeightConfig();
        for (JsonNode rule : runtimeProperties.childObjects(config.path("suppressWhen"))) {
            String field = runtimeProperties.text(rule, "field", "");
            if (field.isBlank()) {
                continue;
            }
            String value = value(item, field);
            if (!value.isBlank() && runtimeProperties.childTextValues(rule.path("values")).contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String toCamelCase(String key) {
        if (key == null || key.isBlank() || !key.contains("_")) {
            return key == null ? "" : key;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
