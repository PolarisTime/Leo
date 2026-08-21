package com.leo.erp.system.printtemplate.service;

import java.util.List;

/**
 * 打印数据源描述：表名、外键与显式打印列清单。
 * <p>
 * 列清单来自 print-runtime.json 的 printColumns / itemPrintColumns 声明，
 * 用于替代 SELECT *，避免把审计与技术列（created_by、version 等）带进打印数据。
 */
record PrintRecordSource(
        String tableName,
        String itemTableName,
        String itemFkColumn,
        boolean productPrintItems,
        boolean printItemAmount,
        String settlementModeColumn,
        String allocationAmountColumn,
        List<String> printColumns,
        List<String> itemPrintColumns
) {
}
