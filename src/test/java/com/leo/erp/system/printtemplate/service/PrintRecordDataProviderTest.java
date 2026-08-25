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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PrintRecordDataProviderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
    private final PrintRecordDataProvider provider = new PrintRecordDataProvider(jdbc, formatter, runtimeProperties);

    @Test
    void shouldExposeCustomerStatementGroupingFieldsForPrintJob() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("record_id", 2L),
                Map.entry("source_no", "SO-001"),
                Map.entry("source_sales_order_item_id", 3L),
                Map.entry("delivery_date", "2026-08-01"),
                Map.entry("brand", "沙钢"),
                Map.entry("quantity_unit", "件"),
                Map.entry("quantity", 5),
                Map.entry("weight_ton", "1.2"),
                Map.entry("unit_price", "3500.00"),
                Map.entry("amount", "4200.00")
        )));

        List<PrintRecordItem> items = provider.listPrintItems("customer-statement", List.of(2L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.sourceNo()).isEqualTo("SO-001");
            assertThat(item.deliveryDate()).isEqualTo("2026-08-01");
            assertThat(item.quantityUnit()).isEqualTo("件");
            assertThat(item.sourceSalesOrderItemId()).isEqualTo("3");
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("source_no")
                .contains("delivery_date")
                .contains("quantity_unit");
    }

    @Test
    void shouldExposeFreightStatementGroupFieldsForPrintJob() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 1L),
                Map.entry("record_id", 2L),
                Map.entry("source_no", "FB-001"),
                Map.entry("source_freight_bill_id", 3L),
                Map.entry("customer_name", "客户甲"),
                Map.entry("project_name", "项目一"),
                Map.entry("quantity_unit", "件"),
                Map.entry("source_freight_bill_unit_price", "18.00"),
                Map.entry("source_freight_bill_total_freight", "2200.00")
        )));

        List<PrintRecordItem> items = provider.listPrintItems("freight-statement", List.of(2L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.sourceNo()).isEqualTo("FB-001");
            assertThat(item.customerName()).isEqualTo("客户甲");
            assertThat(item.projectName()).isEqualTo("项目一");
            assertThat(item.sourceFreightBillId()).isEqualTo("3");
            assertThat(item.sourceFreightBillUnitPrice()).isEqualTo("18.00");
            assertThat(item.sourceFreightBillTotalFreight()).isEqualTo("2200.00");
        });
    }
}
