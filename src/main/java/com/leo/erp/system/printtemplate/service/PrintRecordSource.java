package com.leo.erp.system.printtemplate.service;

record PrintRecordSource(
        String tableName,
        String itemTableName,
        String itemFkColumn,
        boolean productPrintItems,
        boolean printItemAmount,
        String settlementModeColumn,
        String allocationAmountColumn
) {
}
