package com.leo.erp.master.material.web.dto;

import java.util.List;

/**
 * 商品导入预览（dry-run）结果资源：不落库、不写历史。
 */
public record MaterialImportPreviewResponse(
        int totalRows,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<MaterialImportPreviewRowResponse> rows
) {
}
