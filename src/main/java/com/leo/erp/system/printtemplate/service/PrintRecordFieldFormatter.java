package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.leo.erp.common.support.DateTimeFormatSupport;
import com.leo.erp.common.support.PrecisionConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    /**
     * 打印数据取值口径：日期与时间统一转成面向用户的展示口径（{@code X年X月X日}[ {@code HH:mm:ss}]），
     * 其余值保持原样。
     *
     * <p>打印数据只用于套打/PDF/Excel 输出，不承载机器语义（排序键、对象存储 key、接口 DTO 均取自
     * 其它路径），因此在此预格式化是安全的：{@code chineseDate} 分支对已含「年」的值原样返回，
     * 预格式化不会二次包装。</p>
     *
     * <p>{@code Timestamp}/{@code LocalDateTime} 落在午夜时按纯日期输出：本系统多张单据把
     * {@code LocalDate} 语义存成 {@code timestamp}（如 {@code lg_freight_bill.bill_time}），
     * 打印「单据日期」时不应显示无意义的 {@code 00:00:00}；该口径与
     * {@code ExcelExportService#writeDateTime} 的 dateOnly 判定一致。</p>
     */
    String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof java.sql.Date date) {
            return DateTimeFormatSupport.displayDate(date.toLocalDate());
        }
        if (value instanceof Timestamp timestamp) {
            return displayDateTime(timestamp.toLocalDateTime());
        }
        if (value instanceof LocalDate date) {
            return DateTimeFormatSupport.displayDate(date);
        }
        if (value instanceof LocalDateTime dateTime) {
            return displayDateTime(dateTime);
        }
        return String.valueOf(value);
    }

    private String displayDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return LocalTime.MIDNIGHT.equals(value.toLocalTime())
                ? DateTimeFormatSupport.displayDate(value.toLocalDate())
                : DateTimeFormatSupport.displayDateTime(value);
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
