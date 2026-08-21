package com.leo.erp.master.material.service;

import java.util.List;

public record MaterialCsvImportResult(
        int totalRows,
        int successCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<Failure> failures,
        List<RowTrace> rows
) {

    public record Failure(int rowNumber, String materialCode, String reason) {
    }

    /** 单行导入轨迹：outcome 为 null 表示该行失败，reason 记录原因。 */
    public record RowTrace(int rowNumber, String materialCode, String brand, String material,
                           String spec, String length, String outcome, String reason) {

        public static RowTrace failed(int rowNumber, String materialCode, String reason) {
            return new RowTrace(rowNumber, materialCode, null, null, null, null, "FAILED", reason);
        }
    }
}
