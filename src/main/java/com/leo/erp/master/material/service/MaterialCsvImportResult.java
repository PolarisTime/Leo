package com.leo.erp.master.material.service;

import java.util.List;

public record MaterialCsvImportResult(
        int totalRows,
        int successCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<Failure> failures
) {

    public record Failure(int rowNumber, String materialCode, String reason) {
    }
}
