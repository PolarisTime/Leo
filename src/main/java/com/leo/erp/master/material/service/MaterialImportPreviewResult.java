package com.leo.erp.master.material.service;

import com.leo.erp.master.material.domain.MaterialSnapshot;

import java.util.List;

/**
 * 商品导入预览结果（dry-run）：不落库、不写历史，返回每行判定结果与字段差异。
 */
public record MaterialImportPreviewResult(
        int totalRows,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<Row> rows
) {

    public record Row(
            int rowNumber,
            String materialCode,
            String brand,
            String material,
            String spec,
            String length,
            String outcome,
            Long materialId,
            List<MaterialSnapshot.FieldChange> changes,
            String reason
    ) {

        static Row failed(int rowNumber, String materialCode, String reason) {
            return new Row(rowNumber, materialCode, null, null, null, null, "FAILED", null, List.of(), reason);
        }
    }
}
