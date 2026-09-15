package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打印字段边界回归测试：前端页面列配置变化不得影响后端打印字段白名单。
 */
class PrintRuntimeFieldContractTest {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private PrintRuntimeProperties runtimeProperties;
    private JdbcTemplate jdbc;
    private PrintRecordDataProvider provider;

    @BeforeEach
    void setUp() {
        runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
        PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
        jdbc = mock(JdbcTemplate.class);
        provider = new PrintRecordDataProvider(jdbc, formatter, runtimeProperties);
    }

    @Test
    void salesOrderEnrichers_shouldExposeTotalChargeAmount() {
        assertThat(runtimeProperties.childObjects(runtimeProperties.enrichers("sales-order")))
                .as("销售订单打印必须回填附加费用合计 totalChargeAmount")
                .anySatisfy(rule -> assertThat(rule.path("targetField").asText())
                        .isEqualTo("totalChargeAmount"));
    }

    @Test
    void salesOrderEnrichers_shouldExposeChargeItemsText() {
        assertThat(runtimeProperties.childObjects(runtimeProperties.enrichers("sales-order")))
                .as("销售订单打印必须回填附加费用明细 chargeItemsText")
                .anySatisfy(rule -> assertThat(rule.path("targetField").asText())
                        .isEqualTo("chargeItemsText"));
    }

    @Test
    void pageHiddenFields_shouldRemainInSalesOrderPrintColumns() {
        PrintRecordSource source = runtimeProperties.source("sales-order");

        assertThat(source.itemPrintColumns())
                .contains("material_code", "batch_no", "warehouse_name", "brand", "spec")
                .startsWith("id", "order_id");
    }

    @Test
    void pageHiddenFields_shouldRemainInPurchaseOrderPrintColumns() {
        PrintRecordSource source = runtimeProperties.source("purchase-order");

        assertThat(source.itemPrintColumns())
                .contains("material_code", "batch_no", "warehouse_name", "actual_weight_ton");
    }

    @Test
    void pageHiddenFields_shouldRemainInPurchaseInboundPrintColumns() {
        PrintRecordSource source = runtimeProperties.source("purchase-inbound");

        assertThat(source.itemPrintColumns())
                .contains("material_code", "batch_no", "warehouse_name", "weigh_weight_ton",
                        "weight_adjustment_ton", "settlement_mode");
    }

    @Test
    void allModulePrintColumns_shouldBeExplicitSnakeCaseIdentifiers() {
        for (String module : runtimeProperties.printableModules()) {
            PrintRecordSource source = runtimeProperties.source(module);
            assertThat(source.printColumns())
                    .as("printColumns of %s", module)
                    .isNotEmpty()
                    .allMatch(column -> SQL_IDENTIFIER.matcher(column).matches());
            assertThat(source.itemPrintColumns())
                    .as("itemPrintColumns of %s", module)
                    .isNotEmpty()
                    .allMatch(column -> SQL_IDENTIFIER.matcher(column).matches());
        }
    }

    @Test
    void printableModules_shouldCoverAllTradeModules() {
        assertThat(runtimeProperties.printableModules())
                .contains("sales-order", "purchase-order", "purchase-inbound",
                        "sales-outbound", "sales-return", "customer-statement", "freight-statement");
    }

    @Test
    void salesReturnEnrichers_shouldResolveSourceDocumentNumbers() {
        List<String> targets = runtimeProperties.childObjects(runtimeProperties.enrichers("sales-return"))
                .stream()
                .flatMap(rule -> runtimeProperties.childFields(rule.path("targetFields")).keySet().stream())
                .toList();

        assertThat(targets)
                .as("销售退货单打印必须回填来源出库号、来源订单号与来源物流单号")
                .contains("sourceSalesOutboundNo", "sourceSalesOrderNo", "sourceFreightBillNo");
    }

    @Test
    void salesReturn_shouldExposeBusinessNoAndDateParts() {
        assertThat(runtimeProperties.childTextValues(
                runtimeProperties.topLevelFields().path("businessNoKeys")))
                .contains("returnNo");
        assertThat(runtimeProperties.childTextValues(
                runtimeProperties.topLevelFields().path("dateParts").path("sourceKeys")))
                .contains("returnDate");
    }

    @Test
    void salesOrderPrintSql_shouldKeepHiddenFields() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", 1L),
                        Map.entry("order_id", 2L)
                )))
                .thenReturn(List.of());

        provider.loadRecord("sales-order", 2L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues().get(1))
                .contains("material_code")
                .contains("batch_no")
                .contains("warehouse_name")
                .doesNotContain("materialCode")
                .doesNotContain("batchNo");
    }
}
