package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
class PrintRecordDataProvider {

    private final JdbcTemplate jdbc;
    private final PrintRecordFieldFormatter formatter;
    private final PrintRuntimeProperties runtimeProperties;

    PrintRecordDataProvider(JdbcTemplate jdbc, PrintRecordFieldFormatter formatter, PrintRuntimeProperties runtimeProperties) {
        this.jdbc = jdbc;
        this.formatter = formatter;
        this.runtimeProperties = runtimeProperties;
    }

    void requireSupported(String moduleKey) {
        source(moduleKey);
    }

    /**
     * 一次查询同时完成存在性校验与数据加载：记录不存在时抛出与
     * assertRecordExists 相同的业务异常，避免两次重复查询。
     */
    PrintRecordData loadRecord(String moduleKey, Long recordId) {
        PrintRecordSource source = source(moduleKey);
        List<Map<String, Object>> rows = jdbc.queryForList(recordSql(source), recordId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        return new PrintRecordData(formatter.toCamelStringMap(rows.get(0)), loadItems(source, recordId));
    }

    List<PrintRecordItem> listPrintItems(String moduleKey, List<Long> recordIds) {
        PrintRecordSource source = source(moduleKey);
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(recordIds.size(), "?"));
        List<Map<String, Object>> rows = jdbc.queryForList(printItemSql(moduleKey, source, placeholders), recordIds.toArray());
        return rows.stream()
                .map(this::toPrintRecordItem)
                .toList();
    }

    private String recordSql(PrintRecordSource source) {
        return "SELECT " + String.join(", ", source.printColumns())
                + " FROM " + source.tableName()
                + " WHERE id = ? AND deleted_flag = FALSE";
    }

    private PrintRecordSource source(String moduleKey) {
        return runtimeProperties.source(moduleKey);
    }

    private List<Map<String, String>> loadItems(PrintRecordSource source, Long recordId) {
        List<Map<String, String>> result = new ArrayList<>();
        String sql = "SELECT " + String.join(", ", source.itemPrintColumns())
                + " FROM " + source.itemTableName()
                + " WHERE " + source.itemFkColumn() + " = ? ORDER BY line_no ASC, id ASC";
        var items = jdbc.queryForList(sql, recordId);
        for (var item : items) {
            result.add(formatter.toCamelStringMap(item));
        }
        return result;
    }

    private String printItemSql(String moduleKey, PrintRecordSource source, String placeholders) {
        if (!source.productPrintItems()) {
            String amountColumn = source.allocationAmountColumn().isBlank() ? "''" : source.allocationAmountColumn();
            return "SELECT id, " + source.itemFkColumn() + " AS record_id, "
                    + "'' AS brand, '' AS category, '' AS settlement_mode, '' AS material, '' AS spec, '' AS length, "
                    + "'' AS quantity, '' AS piece_weight_ton, '' AS weight_ton, '' AS unit_price, "
                    + amountColumn + " AS amount, " + statementGroupingColumns(moduleKey) + " "
                    + "FROM " + source.itemTableName()
                    + " WHERE " + source.itemFkColumn() + " IN (" + placeholders + ")"
                    + " ORDER BY " + source.itemFkColumn() + " ASC, line_no ASC, id ASC";
        }
        String unitPrice = source.printItemAmount() ? "unit_price" : "''";
        String amount = source.printItemAmount() ? "amount" : "''";
        String settlementMode = source.settlementModeColumn().isBlank() ? "''" : source.settlementModeColumn();
        return "SELECT id, " + source.itemFkColumn() + " AS record_id, brand, category, material, spec, length, "
                + settlementMode + " AS settlement_mode, "
                + "quantity, piece_weight_ton, weight_ton, " + unitPrice + " AS unit_price, " + amount + " AS amount, "
                + statementGroupingColumns(moduleKey) + " "
                + "FROM " + source.itemTableName()
                + " WHERE " + source.itemFkColumn() + " IN (" + placeholders + ")"
                + " ORDER BY " + source.itemFkColumn() + " ASC, line_no ASC, id ASC";
    }

    private String statementGroupingColumns(String moduleKey) {
        if ("customer-statement".equals(moduleKey)) {
            return "source_no, quantity_unit, source_sales_order_item_id, "
                    + "COALESCE((SELECT so.delivery_date::text FROM so_sales_order_item soi "
                    + "JOIN so_sales_order so ON so.id = soi.order_id "
                    + "WHERE soi.id = source_sales_order_item_id AND soi.deleted_flag = FALSE "
                    + "AND so.deleted_flag = FALSE LIMIT 1), '') AS delivery_date, "
                    + "'' AS customer_name, '' AS project_name, '' AS source_freight_bill_id, "
                    + "'' AS source_freight_bill_unit_price, '' AS source_freight_bill_total_freight";
        }
        if ("freight-statement".equals(moduleKey)) {
            return "source_no, customer_name, project_name, quantity_unit, source_freight_bill_id, "
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
                + "'' AS source_freight_bill_unit_price, '' AS source_freight_bill_total_freight";
    }

    private PrintRecordItem toPrintRecordItem(Map<String, Object> row) {
        Map<String, String> item = formatter.enrichItemPrintFields(formatter.toCamelStringMap(row));
        return new PrintRecordItem(
                formatter.value(item, "id"),
                formatter.value(item, "recordId"),
                formatter.value(item, "brand"),
                formatter.value(item, "category"),
                formatter.value(item, "settlementMode"),
                formatter.value(item, "material"),
                formatter.value(item, "spec"),
                formatter.value(item, "length"),
                formatter.value(item, "quantity"),
                formatter.value(item, "pieceWeightTon"),
                formatter.value(item, "weightTon"),
                formatter.value(item, "unitPrice"),
                formatter.value(item, "amount"),
                formatter.value(item, "sourceNo"),
                formatter.value(item, "deliveryDate"),
                formatter.value(item, "quantityUnit"),
                formatter.value(item, "customerName"),
                formatter.value(item, "projectName"),
                formatter.value(item, "sourceSalesOrderItemId"),
                formatter.value(item, "sourceFreightBillId"),
                formatter.value(item, "sourceFreightBillUnitPrice"),
                formatter.value(item, "sourceFreightBillTotalFreight")
        );
    }
}
