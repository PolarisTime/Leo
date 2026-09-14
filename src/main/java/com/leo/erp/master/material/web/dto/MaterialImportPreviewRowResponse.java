package com.leo.erp.master.material.web.dto;

import java.util.List;

/**
 * 商品导入预览单行结果：outcome 为 CREATED/UPDATED/SKIPPED/FAILED；
 * FAILED 时 reason 必填，其余为 null；changes 记录字段前后差异。
 */
public record MaterialImportPreviewRowResponse(
        int rowNumber,
        String materialCode,
        String brand,
        String material,
        String spec,
        String length,
        String outcome,
        Long materialId,
        List<MaterialFieldChangeResponse> changes,
        String reason
) {
}
