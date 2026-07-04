package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrintRecordFieldFormatterTest {

    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);

    @Test
    void shouldSuppressPieceWeightWhenConfiguredFieldMatches() {
        Map<String, String> item = new HashMap<>();
        item.put("settlementMode", "过磅");
        item.put("pieceWeightTon", "0.1234");
        item.put("weightTon", "1.23456");
        item.put("unitPrice", "12.345");
        item.put("amount", "99.999");

        Map<String, String> enriched = formatter.enrichItemPrintFields(item);

        assertThat(enriched.get("pieceWeightTon")).isEqualTo("-");
        assertThat(enriched.get("weightTon")).isEqualTo(format("1.23456", PrintRecordFieldFormatter.WEIGHT_SCALE));
        assertThat(enriched.get("unitPrice")).isEqualTo("12.35");
        assertThat(enriched.get("amount")).isEqualTo("100.00");
    }

    @Test
    void shouldDerivePieceWeightWhenQuantityAndWeightArePositive() {
        Map<String, String> item = new HashMap<>();
        item.put("quantity", "4");
        item.put("pieceWeightTon", "");
        item.put("weightTon", "1.000");
        item.put("unitPrice", "12.345");
        item.put("amount", "0");

        Map<String, String> enriched = formatter.enrichItemPrintFields(item);

        assertThat(enriched.get("pieceWeightTon")).isEqualTo(format("0.25", PrintRecordFieldFormatter.WEIGHT_SCALE));
        assertThat(enriched.get("weightTon")).isEqualTo(format("1.000", PrintRecordFieldFormatter.WEIGHT_SCALE));
        assertThat(enriched.get("unitPrice")).isEqualTo("12.35");
        assertThat(enriched.get("amount")).isEmpty();
    }

    @Test
    void shouldLeavePieceWeightBlankWhenQuantityIsZero() {
        Map<String, String> item = new HashMap<>();
        item.put("quantity", "0");
        item.put("pieceWeightTon", "");
        item.put("weightTon", "1.000");

        Map<String, String> enriched = formatter.enrichItemPrintFields(item);

        assertThat(enriched.get("pieceWeightTon")).isEmpty();
    }

    @Test
    void shouldIgnoreEmptyDashAndMissingDecimalFields() {
        Map<String, String> row = new HashMap<>();
        row.put("blank", " ");
        row.put("dash", "-");
        row.put("zero", "0.000");

        formatter.formatDecimalField(row, "missing", 2);
        formatter.formatDecimalField(row, "blank", 2);
        formatter.formatDecimalField(row, "dash", 2);
        formatter.formatDecimalField(row, "zero", 2);

        assertThat(row).doesNotContainKey("missing");
        assertThat(row.get("blank")).isEqualTo(" ");
        assertThat(row.get("dash")).isEqualTo("-");
        assertThat(row.get("zero")).isEmpty();
    }

    @Test
    void shouldParseInvalidDecimalAsZero() {
        assertThat(formatter.decimal(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(formatter.decimal(" ")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(formatter.decimal("-")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(formatter.decimal("invalid")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(formatter.decimal(" 12.30 ")).isEqualByComparingTo("12.30");
    }

    @Test
    void shouldFormatDecimalsAndQuantities() {
        assertThat(formatter.formatDecimal(BigDecimal.ZERO, 2)).isEmpty();
        assertThat(formatter.formatDecimal(new BigDecimal("1.235"), 2)).isEqualTo("1.24");
        assertThat(formatter.formatQuantity(BigDecimal.ZERO)).isEmpty();
        assertThat(formatter.formatQuantity(new BigDecimal("12.300"))).isEqualTo("12.3");
    }

    @Test
    void shouldConvertRowsToCamelStringMapAndSkipNullValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("project_name", "项目");
        row.put("plain", "值");
        row.put("", "blank-key");
        row.put(null, "null-key");
        row.put("ignored", null);

        Map<String, String> result = formatter.toCamelStringMap(row);

        assertThat(result)
                .containsEntry("projectName", "项目")
                .containsEntry("plain", "值")
                .containsEntry("", "null-key")
                .doesNotContainKey("ignored");
    }

    @Test
    void shouldConvertSupportedValueTypesToString() {
        LocalDate date = LocalDate.of(2026, 7, 3);
        LocalDateTime dateTime = LocalDateTime.of(2026, 7, 3, 12, 30, 15);

        assertThat(formatter.stringValue(null)).isEmpty();
        assertThat(formatter.stringValue(new BigDecimal("12.300"))).isEqualTo("12.3");
        assertThat(formatter.stringValue(java.sql.Date.valueOf(date))).isEqualTo("2026-07-03");
        assertThat(formatter.stringValue(Timestamp.valueOf(dateTime))).isEqualTo("2026-07-03T12:30:15");
        assertThat(formatter.stringValue(date)).isEqualTo("2026-07-03");
        assertThat(formatter.stringValue(dateTime)).isEqualTo("2026-07-03T12:30:15");
        assertThat(formatter.stringValue(123)).isEqualTo("123");
    }

    @Test
    void shouldSkipSuppressRuleWithoutFieldAndUseNextMatchingRule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode config = objectMapper.readTree("""
                {
                  "replacement": "-",
                  "suppressWhen": [
                    {"values": ["ignored"]},
                    {"field": "category", "values": ["盘螺"]}
                  ]
                }
                """);
        JsonNode firstRule = config.path("suppressWhen").get(0);
        JsonNode secondRule = config.path("suppressWhen").get(1);
        PrintRuntimeProperties properties = mock(PrintRuntimeProperties.class);
        when(properties.pieceWeightConfig()).thenReturn(config);
        when(properties.childObjects(config.path("suppressWhen"))).thenReturn(List.of(firstRule, secondRule));
        when(properties.text(firstRule, "field", "")).thenReturn("");
        when(properties.text(secondRule, "field", "")).thenReturn("category");
        when(properties.childTextValues(secondRule.path("values"))).thenReturn(List.of("盘螺"));
        when(properties.text(config, "replacement", "-")).thenReturn("-");
        PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(properties);

        Map<String, String> enriched = formatter.enrichItemPrintFields(Map.of("category", "盘螺", "pieceWeightTon", "0.120"));

        assertThat(enriched.get("pieceWeightTon")).isEqualTo("-");
    }

    @Test
    void shouldReadMissingValueAsEmptyString() {
        assertThat(formatter.value(Map.of(), "missing")).isEmpty();
    }

    private String format(String value, int scale) {
        return new BigDecimal(value).setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
