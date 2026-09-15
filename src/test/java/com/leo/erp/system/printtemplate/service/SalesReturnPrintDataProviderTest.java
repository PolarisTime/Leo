package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售退货单（sales-return）打印数据来源回归测试：模块注册、单据头/明细字段与空记录处理。
 */
class SalesReturnPrintDataProviderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(new ObjectMapper());
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
    private final PrintRecordDataProvider provider = new PrintRecordDataProvider(jdbc, formatter, runtimeProperties);

    @Test
    void salesReturn_shouldBeRegisteredAsPrintableModule() {
        assertThat(runtimeProperties.printableModules()).contains("sales-return");

        PrintRecordSource source = runtimeProperties.source("sales-return");

        assertThat(source.tableName()).isEqualTo("so_sales_return");
        assertThat(source.itemTableName()).isEqualTo("so_sales_return_item");
        assertThat(source.itemFkColumn()).isEqualTo("return_id");
        assertThat(source.productPrintItems()).isTrue();
        assertThat(source.printItemAmount()).isTrue();
        assertThat(source.printColumns())
                .contains("id", "return_no", "return_date", "customer_name", "project_name",
                        "settlement_company_id", "settlement_company_name", "total_weight",
                        "total_amount", "remark")
                .startsWith("id", "return_no");
        assertThat(source.itemPrintColumns())
                .contains("id", "return_id", "material_code", "brand", "category", "material",
                        "spec", "batch_no", "warehouse_name", "quantity", "quantity_unit",
                        "piece_weight_ton", "weight_ton", "unit_price", "amount",
                        "source_sales_outbound_item_id", "source_sales_order_item_id",
                        "source_freight_bill_id")
                .startsWith("id", "return_id");
    }

    @Test
    void loadRecord_shouldReturnSalesReturnHeaderAndItems() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", 900L),
                        Map.entry("return_no", "RT20260915001"),
                        Map.entry("sales_order_no", "SO20260915001"),
                        Map.entry("customer_id", 11L),
                        Map.entry("customer_name", "客户甲"),
                        Map.entry("project_id", 12L),
                        Map.entry("project_name", "项目一"),
                        Map.entry("warehouse_id", 13L),
                        Map.entry("warehouse_name", "一号仓"),
                        Map.entry("settlement_company_id", 14L),
                        Map.entry("settlement_company_name", "嘉兴颖捷建材有限公司"),
                        Map.entry("return_date", "2026-09-15"),
                        Map.entry("total_weight", new BigDecimal("3.50000000")),
                        Map.entry("total_amount", new BigDecimal("12345.67")),
                        Map.entry("status", "已审核"),
                        Map.entry("remark", "整批退货")
                )))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", 1L),
                        Map.entry("return_id", 900L),
                        Map.entry("line_no", 1),
                        Map.entry("material_id", 21L),
                        Map.entry("material_code", "M-001"),
                        Map.entry("brand", "沙钢"),
                        Map.entry("category", "螺纹钢"),
                        Map.entry("material", "HRB400"),
                        Map.entry("spec", "8"),
                        Map.entry("length", "12"),
                        Map.entry("unit", "吨"),
                        Map.entry("source_sales_outbound_item_id", 31L),
                        Map.entry("source_sales_order_item_id", 32L),
                        Map.entry("source_freight_bill_id", 33L),
                        Map.entry("settlement_company_id", 14L),
                        Map.entry("settlement_company_name", "嘉兴颖捷建材有限公司"),
                        Map.entry("warehouse_id", 13L),
                        Map.entry("warehouse_name", "一号仓"),
                        Map.entry("batch_no", "B-20260915"),
                        Map.entry("batch_no_normalized", "B-20260915"),
                        Map.entry("quantity", 5),
                        Map.entry("quantity_unit", "件"),
                        Map.entry("piece_weight_ton", new BigDecimal("0.70000000")),
                        Map.entry("pieces_per_bundle", 6),
                        Map.entry("weight_ton", new BigDecimal("3.50000000")),
                        Map.entry("unit_price", new BigDecimal("3527.33")),
                        Map.entry("amount", new BigDecimal("12345.67"))
                )));

        PrintRecordData data = provider.loadRecord("sales-return", 900L);

        assertThat(data.data())
                .containsEntry("returnNo", "RT20260915001")
                .containsEntry("salesOrderNo", "SO20260915001")
                .containsEntry("customerName", "客户甲")
                .containsEntry("projectName", "项目一")
                .containsEntry("warehouseName", "一号仓")
                .containsEntry("settlementCompanyId", "14")
                .containsEntry("settlementCompanyName", "嘉兴颖捷建材有限公司")
                .containsEntry("returnDate", "2026-09-15")
                .containsEntry("totalWeight", "3.5")
                .containsEntry("totalAmount", "12345.67")
                .containsEntry("status", "已审核")
                .containsEntry("remark", "整批退货");

        assertThat(data.items()).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("materialCode", "M-001");
            assertThat(item).containsEntry("brand", "沙钢");
            assertThat(item).containsEntry("warehouseName", "一号仓");
            assertThat(item).containsEntry("batchNo", "B-20260915");
            assertThat(item).containsEntry("quantity", "5");
            assertThat(item).containsEntry("pieceWeightTon", "0.7");
            assertThat(item).containsEntry("weightTon", "3.5");
            assertThat(item).containsEntry("unitPrice", "3527.33");
            assertThat(item).containsEntry("amount", "12345.67");
            assertThat(item).containsEntry("sourceSalesOutboundItemId", "31");
            assertThat(item).containsEntry("sourceSalesOrderItemId", "32");
            assertThat(item).containsEntry("sourceFreightBillId", "33");
        });

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getAllValues().get(0))
                .contains("FROM so_sales_return")
                .contains("WHERE id = ? AND deleted_flag = FALSE");
        assertThat(sql.getAllValues().get(1))
                .contains("FROM so_sales_return_item")
                .contains("return_id = ?")
                .contains("ORDER BY line_no ASC, id ASC")
                .doesNotContain("returnNo");
    }

    @Test
    void loadRecord_shouldThrowWhenSalesReturnMissing() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> provider.loadRecord("sales-return", 900L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务记录不存在");
    }

    @Test
    void listPrintItems_shouldMapSalesReturnItemRows() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", 1L),
                        Map.entry("record_id", 900L),
                        Map.entry("brand", "沙钢"),
                        Map.entry("category", "螺纹钢"),
                        Map.entry("material", "HRB400"),
                        Map.entry("spec", "8"),
                        Map.entry("length", "12"),
                        Map.entry("quantity", 5),
                        Map.entry("piece_weight_ton", "0.7"),
                        Map.entry("weight_ton", "3.5"),
                        Map.entry("unit_price", "3527.33"),
                        Map.entry("amount", "12345.67")
                )));

        List<PrintRecordItem> items = provider.listPrintItems("sales-return", List.of(900L));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.recordId()).isEqualTo("900");
            assertThat(item.brand()).isEqualTo("沙钢");
            assertThat(item.material()).isEqualTo("HRB400");
            assertThat(item.spec()).isEqualTo("8");
            assertThat(item.quantity()).isEqualTo("5");
            assertThat(item.pieceWeightTon()).isEqualTo("0.700");
            assertThat(item.weightTon()).isEqualTo("3.500");
            assertThat(item.unitPrice()).isEqualTo("3527.33");
            assertThat(item.amount()).isEqualTo("12345.67");
        });

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("FROM so_sales_return_item")
                .contains("return_id IN (?)");
    }

    @Test
    void listPrintItems_shouldReturnEmptyWhenNoRecordIds() {
        assertThat(provider.listPrintItems("sales-return", List.of())).isEmpty();
        assertThat(provider.listPrintItems("sales-return", null)).isEmpty();
    }

    @Test
    void requireSupported_shouldAcceptSalesReturnAndRejectUnknownModule() {
        provider.requireSupported("sales-return");

        assertThatThrownBy(() -> provider.requireSupported("unknown-module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的打印模块");
    }
}
