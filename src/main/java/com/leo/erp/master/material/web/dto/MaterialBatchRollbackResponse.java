package com.leo.erp.master.material.web.dto;

/**
 * 导入批次回滚结果：CREATED 行软删，UPDATED 行还原 before 快照。
 */
public record MaterialBatchRollbackResponse(
        String importBatchNo,
        int totalRows,
        int createdRolledBack,
        int updatedRestored,
        int missing
) {
}
