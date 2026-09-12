package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单附加费用打印回填：print-runtime.json 的 dataLookup 必须把费用合计写入打印数据。
 */
class PrintRecordEnricherTest {

    private static final String CHARGE_TABLE = "bd_document_charge_item";

    private JdbcTemplate jdbc;
    private PrintRecordEnricher enricher;

    @BeforeEach
    void setUp() {
        PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
        PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
        jdbc = mock(JdbcTemplate.class);
        enricher = new PrintRecordEnricher(jdbc, formatter, runtimeProperties);
    }

    @Test
    void enrich_shouldFillTotalChargeAmountFromChargeItems() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains(CHARGE_TABLE) ? List.of("150.00") : List.of();
        });

        Map<String, String> data = new HashMap<>();
        data.put("id", "42");

        enricher.enrich("sales-order", data, List.of());

        assertThat(data.get("totalChargeAmount")).isEqualTo("150.00");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sqlCaptor.capture(), eq(String.class), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains(CHARGE_TABLE)
                .contains("module_key = 'sales-order'")
                .contains("deleted_flag = FALSE")
                .contains("to_char"));
    }

    @Test
    void enrich_shouldSkipChargeLookupWhenRecordIdMissing() {
        Map<String, String> data = new HashMap<>();

        enricher.enrich("sales-order", data, List.of());

        assertThat(data).doesNotContainKey("totalChargeAmount");
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any(Object[].class));
    }

    @Test
    void enrich_shouldBuildChargeItemSummaryWithChineseUpperAmount() {
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("1250.50"));
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of("charge_name", "运费", "amount", new BigDecimal("1000.00")),
                        Map.of("charge_name", "装卸费", "amount", new BigDecimal("250.50"))
                ));

        Map<String, String> data = new HashMap<>();
        data.put("id", "42");

        enricher.enrich("sales-order", data, List.of());

        assertThat(data.get("chargeItemsText"))
                .isEqualTo("    |    附加费用：运费 1000元（大写壹仟元整）、装卸费 250.50元（大写贰佰伍拾元伍角）");
    }

    @Test
    void enrich_shouldHideChargeItemsWhenNone() {
        Map<String, String> data = new HashMap<>();
        data.put("id", "42");

        enricher.enrich("sales-order", data, List.of());

        assertThat(data.get("chargeItemsText")).isEmpty();
    }
}
