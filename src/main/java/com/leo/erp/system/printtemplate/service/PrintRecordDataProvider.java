package com.leo.erp.system.printtemplate.service;

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

    PrintRecordData loadRecord(String moduleKey, Long recordId) {
        PrintRecordSource source = source(moduleKey);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM " + source.tableName() + " WHERE id = ? AND deleted_flag = FALSE", recordId);
        return new PrintRecordData(formatter.toCamelStringMap(row), loadItems(source, recordId));
    }

    List<String> listBrands(String moduleKey, List<Long> recordIds) {
        PrintRecordSource source = source(moduleKey);
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(recordIds.size(), "?"));
        List<String> brands = jdbc.queryForList(
                "SELECT DISTINCT brand FROM " + source.itemTableName()
                        + " WHERE " + source.itemFkColumn() + " IN (" + placeholders + ")"
                        + " AND brand IS NOT NULL AND btrim(brand) <> '' ORDER BY brand ASC",
                String.class,
                recordIds.toArray()
        );
        return brands.stream().map(String::trim).filter(brand -> !brand.isBlank()).toList();
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

    private PrintRecordSource source(String moduleKey) {
        return runtimeProperties.source(moduleKey);
    }

    private List<Map<String, String>> loadItems(PrintRecordSource source, Long recordId) {
        List<Map<String, String>> result = new ArrayList<>();
        String sql = "SELECT * FROM " + source.itemTableName()
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
                    + amountColumn + " AS amount "
                    + "FROM " + source.itemTableName()
                    + " WHERE " + source.itemFkColumn() + " IN (" + placeholders + ")"
                    + " ORDER BY " + source.itemFkColumn() + " ASC, line_no ASC, id ASC";
        }
        String unitPrice = source.printItemAmount() ? "unit_price" : "''";
        String amount = source.printItemAmount() ? "amount" : "''";
        String settlementMode = source.settlementModeColumn().isBlank() ? "''" : source.settlementModeColumn();
        return "SELECT id, " + source.itemFkColumn() + " AS record_id, brand, category, material, spec, length, "
                + settlementMode + " AS settlement_mode, "
                + "quantity, piece_weight_ton, weight_ton, " + unitPrice + " AS unit_price, " + amount + " AS amount "
                + "FROM " + source.itemTableName()
                + " WHERE " + source.itemFkColumn() + " IN (" + placeholders + ")"
                + " ORDER BY " + source.itemFkColumn() + " ASC, line_no ASC, id ASC";
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
                formatter.value(item, "amount")
        );
    }
}
