package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.ModuleKeys;

/**
 * 对账单类打印明细的「分组/来源」列 SQL 片段。
 *
 * <p>客户对账单与物流对账单的明细表结构不同：来源单号、数量单位、交货日期、来源物流单
 * 金额等列需要按模块拼出不同的 SELECT 片段，其余模块统一返回空串别名，保证
 * {@link PrintRecordItem} 的字段契约稳定。</p>
 *
 * <p>这里是该 SQL 片段的**唯一来源**：修改列名/子查询只改这一个类，避免再出现
 * 「客户对账单引用 {@code so_sales_order_item.deleted_flag}（该表无此列）导致打印明细 500」
 * 这类散落在拼接逻辑里的错误。列名与真实库结构的契约由
 * {@code PrintRuntimeColumnContractPostgresTest} 与真实库回归共同守护。</p>
 */
final class PrintStatementGroupingSql {

    private PrintStatementGroupingSql() {
    }

    /**
     * 按模块返回分组列片段（不含尾部逗号）。
     *
     * <p>返回片段会直接拼进 {@code SELECT ... , <片段> FROM ...}，因此必须保证
     * 每个分支的列数/别名一致；未知模块返回全空串别名，与字段契约对齐。</p>
     */
    static String columns(String moduleKey) {
        if (ModuleKeys.CUSTOMER_STATEMENT.equals(moduleKey)) {
            // 销售订单明细表没有 deleted_flag（软删由主表 so_sales_order 控制）；
            // 这里若引用 soi.deleted_flag，打印明细查询会直接 500（BadSqlGrammar）。
            return "source_no, quantity_unit, source_sales_order_item_id, "
                    + "COALESCE((SELECT so.delivery_date::text FROM so_sales_order_item soi "
                    + "JOIN so_sales_order so ON so.id = soi.order_id "
                    + "WHERE soi.id = source_sales_order_item_id "
                    + "AND so.deleted_flag = FALSE LIMIT 1), '') AS delivery_date, "
                    + "'' AS customer_name, '' AS project_name, '' AS source_freight_bill_id, "
                    + "'' AS source_freight_bill_time, "
                    + "'' AS source_freight_bill_unit_price, '' AS source_freight_bill_total_freight";
        }
        if (ModuleKeys.FREIGHT_STATEMENT.equals(moduleKey)) {
            return "source_no, customer_name, project_name, quantity_unit, source_freight_bill_id, "
                    + "source_freight_bill_time::text AS source_freight_bill_time, "
                    + "'' AS delivery_date, '' AS source_sales_order_item_id, "
                    + "COALESCE((SELECT fb.unit_price FROM lg_freight_bill fb "
                    + "WHERE fb.id = source_freight_bill_id AND fb.deleted_flag = FALSE LIMIT 1), 0) "
                    + "AS source_freight_bill_unit_price, "
                    + "COALESCE((SELECT fb.total_freight FROM lg_freight_bill fb "
                    + "WHERE fb.id = source_freight_bill_id AND fb.deleted_flag = FALSE LIMIT 1), 0) "
                    + "AS source_freight_bill_total_freight";
        }
        return "'' AS source_no, '' AS delivery_date, '' AS quantity_unit, '' AS customer_name, "
                + "'' AS project_name, '' AS source_sales_order_item_id, '' AS source_freight_bill_id, "
                + "'' AS source_freight_bill_time, "
                + "'' AS source_freight_bill_unit_price, '' AS source_freight_bill_total_freight";
    }
}
