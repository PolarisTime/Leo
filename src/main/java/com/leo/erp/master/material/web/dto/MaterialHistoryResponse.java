package com.leo.erp.master.material.web.dto;

import com.leo.erp.master.material.domain.MaterialSnapshot;

import java.time.LocalDateTime;

/**
 * 商品主数据版本历史记录：before/after 为字段快照，新建记录 before 为 null。
 * materialId 为雪花 ID，经全局序列化输出为十进制字符串。
 */
public record MaterialHistoryResponse(
        Long id,
        Long materialId,
        String changeSource,
        String changeType,
        MaterialSnapshot before,
        MaterialSnapshot after,
        String importBatchNo,
        String remark,
        Long changedBy,
        LocalDateTime changedAt
) {
}
