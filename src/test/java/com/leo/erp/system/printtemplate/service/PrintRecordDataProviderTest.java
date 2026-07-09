package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

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

class PrintRecordDataProviderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
    private final PrintRecordDataProvider provider = new PrintRecordDataProvider(jdbc, formatter, runtimeProperties);

    @Test
    void shouldLoadRecordAndItems() {
        when(jdbc.queryForMap(anyString(), eq(7L))).thenReturn(Map.of("order_no", "SO-001"));
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of("brand", " 沙钢 ", "weight_ton", "1.2345")));

        PrintRecordData record = provider.loadRecord("sales-order", 7L);

        assertThat(record.data()).containsEntry("orderNo", "SO-001");
        assertThat(record.items()).hasSize(1);
        assertThat(record.items().getFirst()).containsEntry("brand", " 沙钢 ");
    }

    @Test
    void shouldReturnEmptyListsWhenRecordIdsAreMissing() {
        assertThat(provider.listBrands("sales-order", null)).isEmpty();
        assertThat(provider.listBrands("sales-order", List.of())).isEmpty();
        assertThat(provider.listPrintItems("sales-order", null)).isEmpty();
        assertThat(provider.listPrintItems("sales-order", List.of())).isEmpty();

        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void shouldListBrandsAndTrimBlankResults() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq(2L)))
                .thenReturn(List.of(" 沙钢 ", " ", "永钢"));

        List<String> brands = provider.listBrands("sales-order", List.of(1L, 2L));

        assertThat(brands).containsExactly("沙钢", "永钢");
    }

    @Test
    void shouldListAllocationPrintItemsWithConfiguredAmountColumn() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 10L,
                "record_id", 20L,
                "amount", "88.50"
        )));

        List<PrintRecordItem> items = provider.listPrintItems("receipt", List.of(20L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("10");
            assertThat(item.recordId()).isEqualTo("20");
            assertThat(item.amount()).isEqualTo("88.50");
            assertThat(item.brand()).isEmpty();
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("allocated_amount AS amount");
    }

    @Test
    void shouldListAllocationPrintItemsWithBlankAmountColumnFallback() {
        PrintRuntimeProperties properties = mock(PrintRuntimeProperties.class);
        when(properties.source("custom")).thenReturn(new PrintRecordSource(
                "main_table",
                "item_table",
                "main_id",
                false,
                false,
                "",
                ""
        ));
        PrintRecordDataProvider provider = new PrintRecordDataProvider(jdbc, formatter, properties);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 1L,
                "record_id", 2L
        )));

        List<PrintRecordItem> items = provider.listPrintItems("custom", List.of(2L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("1");
            assertThat(item.recordId()).isEqualTo("2");
            assertThat(item.amount()).isEmpty();
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("'' AS amount");
    }

    @Test
    void shouldListProductPrintItemsWithSettlementModeAndAmount() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("record_id", 2L),
                Map.entry("brand", "沙钢"),
                Map.entry("category", "螺纹钢"),
                Map.entry("settlement_mode", "理计"),
                Map.entry("material", "HRB400"),
                Map.entry("spec", "18"),
                Map.entry("quantity", "2"),
                Map.entry("piece_weight_ton", "0.500"),
                Map.entry("weight_ton", "1.000"),
                Map.entry("unit_price", "3500.00"),
                Map.entry("amount", "3500.00")
        )));

        List<PrintRecordItem> items = provider.listPrintItems("purchase-inbound", List.of(2L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.settlementMode()).isEqualTo("理计");
            assertThat(item.unitPrice()).isEqualTo("3500.00");
            assertThat(item.amount()).isEqualTo("3500.00");
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("settlement_mode AS settlement_mode")
                .contains("unit_price AS unit_price, amount AS amount");
    }

    @Test
    void shouldListProductPrintItemsWithoutAmountColumnsWhenModuleDisablesItemAmount() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 1L,
                "record_id", 2L,
                "brand", "物流"
        )));

        List<PrintRecordItem> items = provider.listPrintItems("freight-bill", List.of(2L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.brand()).isEqualTo("物流");
            assertThat(item.unitPrice()).isEmpty();
            assertThat(item.amount()).isEmpty();
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("'' AS unit_price, '' AS amount");
    }
}
