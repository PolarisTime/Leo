package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.ModuleKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对账单打印明细分组 SQL 片段的契约。
 *
 * <p>回归重点：客户对账单分支**不能**引用 {@code soi.deleted_flag}
 * （{@code so_sales_order_item} 没有该列，引用会让打印明细接口 500）。</p>
 */
class PrintStatementGroupingSqlTest {

    @Test
    void customerStatementShouldExposeSourceColumnsWithoutItemDeletedFlag() {
        String sql = PrintStatementGroupingSql.columns(ModuleKeys.CUSTOMER_STATEMENT);

        assertThat(sql)
                .contains("source_no")
                .contains("quantity_unit")
                .contains("source_sales_order_item_id")
                .contains("AS delivery_date")
                .contains("so_sales_order")
                .doesNotContain("soi.deleted_flag");
    }

    @Test
    void freightStatementShouldJoinFreightBillForAmounts() {
        String sql = PrintStatementGroupingSql.columns(ModuleKeys.FREIGHT_STATEMENT);

        assertThat(sql)
                .contains("source_no")
                .contains("customer_name")
                .contains("source_freight_bill_id")
                .contains("source_freight_bill_time::text")
                .contains("lg_freight_bill")
                .contains("AS source_freight_bill_unit_price")
                .contains("AS source_freight_bill_total_freight");
    }

    @Test
    void otherModulesShouldFallBackToEmptyAliasesOnly() {
        String sql = PrintStatementGroupingSql.columns("sales-order");

        assertThat(sql)
                .contains("'' AS source_no")
                .contains("'' AS source_freight_bill_total_freight")
                .doesNotContain("FROM")
                .doesNotContain("JOIN");
    }
}
