package com.leo.erp.master.material.web.dto;

import java.util.List;

public record MaterialImportResultResponse(
        int totalRows,
        int successCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<MaterialImportFailureResponse> failures
) {
}
