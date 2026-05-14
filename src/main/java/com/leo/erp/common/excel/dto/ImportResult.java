package com.leo.erp.common.excel.dto;

import java.util.ArrayList;
import java.util.List;

public record ImportResult(
        int totalRows,
        int successCount,
        int createdCount,
        int updatedCount,
        int failCount,
        List<ImportErrorDetail> errors,
        List<Object> successRows
) {
    public ImportResult() {
        this(0, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>());
    }
}
