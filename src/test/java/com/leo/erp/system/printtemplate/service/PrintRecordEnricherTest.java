package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintRecordEnricherTest {

    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);

    @Test
    void shouldApplyDataLookups() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                eq("SELECT project_address FROM md_project WHERE id = ? AND deleted_flag = FALSE"),
                eq(String.class),
                eq(10L)
        )).thenReturn(List.of("项目地址A"));
        when(jdbc.queryForList(
                eq("SELECT DISTINCT fb.vehicle_plate FROM so_sales_outbound ob JOIN lg_freight_bill_item fbi ON fbi.source_no = ob.outbound_no JOIN lg_freight_bill fb ON fb.id = fbi.bill_id WHERE ob.sales_order_no = ? AND fb.vehicle_plate IS NOT NULL"),
                eq(String.class),
                eq("SO-001")
        )).thenReturn(List.of("沪A12345", "沪B67890"));
        Map<String, String> data = new HashMap<>();
        data.put("projectId", "10");
        data.put("projectName", "项目A");
        data.put("orderNo", "SO-001");
        data.put("settlementCompanyId", "1");
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("sales-order", data, List.of());

        assertThat(data).containsEntry("projectAddress", "项目地址A");
        assertThat(data).containsEntry("vehiclePlate", "沪A12345, 沪B67890");
        assertThat(data).doesNotContainKey("settlementCompanyName");
    }

    @Test
    void shouldApplyDataLookupWithMultipleSourceFields() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom("""
                {
                  "enrichers": {
                    "sales-order": [
                      {
                        "type": "dataLookup",
                        "sourceFields": ["customerName", "projectName"],
                        "targetField": "projectAddress",
                        "sql": "SELECT project_address FROM md_customer WHERE customer_name = ? AND btrim(project_name) = btrim(?) AND deleted_flag = FALSE AND project_address IS NOT NULL ORDER BY id LIMIT 1"
                      }
                    ]
                  }
                }
                """);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                eq("SELECT project_address FROM md_customer WHERE customer_name = ? AND btrim(project_name) = btrim(?) AND deleted_flag = FALSE AND project_address IS NOT NULL ORDER BY id LIMIT 1"),
                eq(String.class),
                eq("客户A"),
                eq("项目A")
        )).thenReturn(List.of("客户资料地址A"));
        Map<String, String> data = new HashMap<>();
        data.put("customerName", "客户A");
        data.put("projectName", "项目A");

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("sales-order", data, List.of());

        assertThat(data).containsEntry("projectAddress", "客户资料地址A");
    }

    @Test
    void shouldTrimControlWhitespaceWhenLookingUpCustomerProjectAddress() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                eq("SELECT project_address FROM md_project WHERE project_name = ? AND deleted_flag = FALSE AND project_address IS NOT NULL ORDER BY id LIMIT 1"),
                eq(String.class),
                eq("项目A")
        )).thenReturn(List.of());
        when(jdbc.queryForList(
                eq("SELECT project_address FROM md_customer WHERE customer_name = ? AND regexp_replace(project_name, '^[[:space:]]+|[[:space:]]+$', '', 'g') = regexp_replace(?, '^[[:space:]]+|[[:space:]]+$', '', 'g') AND deleted_flag = FALSE AND project_address IS NOT NULL ORDER BY id LIMIT 1"),
                eq(String.class),
                eq("客户A"),
                eq("项目A")
        )).thenReturn(List.of("客户资料地址A"));
        Map<String, String> data = new HashMap<>();
        data.put("customerName", "客户A");
        data.put("projectName", "项目A");

        new PrintRecordEnricher(jdbc, formatter, runtimeProperties).enrich("sales-order", data, List.of());

        assertThat(data).containsEntry("projectAddress", "客户资料地址A");
    }

    @Test
    void shouldSkipDataLookupWhenTargetAlreadyPresentOrArgumentInvalid() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, String> data = new HashMap<>();
        data.put("projectId", "not-a-number");
        data.put("projectName", "项目A");
        data.put("projectAddress", "已有地址");
        data.put("orderNo", "SO-001");
        data.put("settlementCompanyId", "bad-id");
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("sales-order", data, List.of());

        assertThat(data).containsEntry("projectAddress", "已有地址");
        assertThat(data).doesNotContainKey("settlementCompanyName");
    }

    @Test
    void shouldApplyItemLookupTargetFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", "SO-001");
        row.put("delivery_date", Date.valueOf(LocalDate.of(2026, 6, 1)));
        when(jdbc.queryForList(
                eq("SELECT order_no, delivery_date FROM so_sales_order WHERE deleted_flag = FALSE AND order_no IN (?)"),
                any(Object[].class)
        )).thenReturn(List.of(row));
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("sourceNo", "SO-001")));
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("customer-statement", new HashMap<>(), items);

        assertThat(items.get(0)).containsEntry("billTime", "2026-06-01");
    }

    @Test
    void shouldApplyFallbackFieldsWhenItemLookupMisses() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        Map<String, String> data = new HashMap<>(Map.of("carrierName", "物流商A"));
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("sourceNo", "FB-001")));
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("freight-statement", data, items);

        assertThat(items.get(0)).containsEntry("carrierName", "物流商A");
    }

    @Test
    void shouldIgnoreLookupRuntimeFailures() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), any())).thenThrow(new IllegalStateException("db down"));
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "项目A");
        data.put("settlementCompanyId", "1");
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("sales-order", data, List.of());

        assertThat(data).doesNotContainKey("projectAddress");
        assertThat(data).doesNotContainKey("settlementCompanyName");
    }

    @Test
    void shouldEnrichSettlementCompanyNameWhenLookupReturnsNonBlankValue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                eq("SELECT company_name FROM sys_company_setting WHERE id = ? AND deleted_flag = FALSE"),
                eq(String.class),
                eq(1L)
        )).thenReturn(List.of("结算主体A"));
        Map<String, String> data = new HashMap<>();
        data.put("settlementCompanyId", "1");
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);

        enricher.enrich("unknown-module", data, List.of());

        assertThat(data).containsEntry("settlementCompanyName", "结算主体A");
    }

    @Test
    void shouldSkipSettlementCompanyNameWhenLookupReturnsNullOrBlank() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                eq("SELECT company_name FROM sys_company_setting WHERE id = ? AND deleted_flag = FALSE"),
                eq(String.class),
                eq(1L)
        )).thenReturn(null);
        when(jdbc.queryForList(
                eq("SELECT company_name FROM sys_company_setting WHERE id = ? AND deleted_flag = FALSE"),
                eq(String.class),
                eq(2L)
        )).thenReturn(List.of(" "));
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);
        Map<String, String> nullResultData = new HashMap<>();
        nullResultData.put("settlementCompanyId", "1");
        Map<String, String> blankResultData = new HashMap<>();
        blankResultData.put("settlementCompanyId", "2");

        enricher.enrich("unknown-module", nullResultData, List.of());
        enricher.enrich("unknown-module", blankResultData, List.of());

        assertThat(nullResultData).doesNotContainKey("settlementCompanyName");
        assertThat(blankResultData).doesNotContainKey("settlementCompanyName");
    }

    @Test
    void shouldSkipInvalidDataLookupRulesAndEmptyResults() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom("""
                {
                  "enrichers": {
                    "custom": [
                      {"type": "dataLookup", "sourceField": "name", "targetField": "", "sql": "ignored"},
                      {"type": "dataLookup", "sourceField": "badLong", "targetField": "bad", "argumentType": "long", "sql": "bad"},
                      {"type": "dataLookup", "sourceField": "blank", "targetField": "blankTarget", "sql": "blank"},
                      {"type": "dataLookup", "sourceField": "name", "targetField": "emptyTarget", "sql": "empty"},
                      {"type": "dataLookup", "sourceField": "name", "targetField": "blankResult", "sql": "blankResult"},
                      {"type": "dataLookup", "sourceField": "name", "targetField": "emptyList", "result": "list", "join": "|", "sql": "list"}
                    ]
                  }
                }
                """);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("empty", String.class, "项目A")).thenReturn(List.of());
        when(jdbc.queryForList("blankResult", String.class, "项目A")).thenReturn(List.of(" "));
        when(jdbc.queryForList("list", String.class, "项目A")).thenReturn(List.of());
        Map<String, String> data = new HashMap<>();
        data.put("name", "项目A");
        data.put("badLong", "not-a-number");
        data.put("blank", " ");
        data.put("settlementCompanyId", null);

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("custom", data, List.of());

        assertThat(data).doesNotContainKeys("bad", "blankTarget", "emptyTarget", "blankResult", "emptyList", "settlementCompanyName");
    }

    @Test
    void shouldIgnoreUnknownEnricherType() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom("""
                {
                  "enrichers": {
                    "custom": [
                      {"type": "unknown", "sourceField": "name", "targetField": "ignored", "sql": "ignored"}
                    ]
                  }
                }
                """);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, String> data = new HashMap<>();

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("custom", data, List.of());

        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any());
        assertThat(data).isEmpty();
    }

    @Test
    void shouldSkipItemLookupWhenItemsOrSourceValuesAreMissing() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom(itemLookupConfig("sourceNo", "code", "{\"target\":\"name\"}", "{}"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintRecordEnricher enricher = new PrintRecordEnricher(jdbc, formatter, properties);

        enricher.enrich("custom", new HashMap<>(), null);
        enricher.enrich("custom", new HashMap<>(), List.of());
        enricher.enrich("custom", new HashMap<>(), List.of(Map.of("sourceNo", " ")));

        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void shouldApplyBlankFallbacksOnlyWhenDataValueIsPresent() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom(itemLookupConfig(
                "sourceNo",
                "missing_key",
                "{\"target\":\"name\"}",
                "{\"carrierName\":\"carrierName\"}"
        ));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("missing_key", "OTHER")));
        Map<String, String> data = new HashMap<>(Map.of("carrierName", " "));
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("sourceNo", "SO-001")));

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("custom", data, items);

        assertThat(items.getFirst()).doesNotContainKey("carrierName");
    }

    @Test
    void shouldIgnoreItemLookupRowsWhenLookupKeyColumnIsBlank() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom(itemLookupConfig("sourceNo", "", "{\"target\":\"name\"}", "{}"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("name", "匹配值")));
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("sourceNo", "SO-001")));

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("custom", new HashMap<>(), items);

        assertThat(items.getFirst()).doesNotContainKey("target");
    }

    @Test
    void shouldIgnoreNullLookupKeysAndNullTargetFieldValues() throws Exception {
        PrintRuntimeProperties properties = runtimePropertiesFrom(itemLookupConfig("sourceNo", "code", "{\"target\":\"missing\"}", "{}"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> rowWithoutKey = new LinkedHashMap<>();
        rowWithoutKey.put("missing", "ignored");
        Map<String, Object> rowWithoutTarget = new LinkedHashMap<>();
        rowWithoutTarget.put("code", "SO-001");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(rowWithoutKey, rowWithoutTarget));
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("sourceNo", "SO-001")));

        new PrintRecordEnricher(jdbc, formatter, properties).enrich("custom", new HashMap<>(), items);

        assertThat(items.getFirst()).doesNotContainKey("target");
    }

    @Test
    void shouldReturnOriginalSqlWhenPlaceholderSizeIsZero() throws Exception {
        PrintRecordEnricher enricher = new PrintRecordEnricher(mock(JdbcTemplate.class), formatter, runtimeProperties);
        Method placeholders = PrintRecordEnricher.class.getDeclaredMethod("placeholders", String.class, int.class);
        placeholders.setAccessible(true);

        assertThat(placeholders.invoke(enricher, "SELECT 1", 0)).isEqualTo("SELECT 1");
    }

    private PrintRuntimeProperties runtimePropertiesFrom(String json) throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readTree(anyString())).thenReturn(new ObjectMapper().readTree(json));
        return new PrintRuntimeProperties(mapper);
    }

    private String itemLookupConfig(String sourceField, String lookupKeyColumn, String targetFields, String fallbackFields) {
        return """
                {
                  "enrichers": {
                    "custom": [
                      {
                        "type": "itemLookupByField",
                        "sourceField": "%s",
                        "sql": "SELECT * FROM table WHERE code IN (:values)",
                        "lookupKeyColumn": "%s",
                        "targetFields": %s,
                        "fallbackFields": %s
                      }
                    ]
                  }
                }
                """.formatted(sourceField, lookupKeyColumn, targetFields, fallbackFields);
    }
}
