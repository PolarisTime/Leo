package com.leo.erp.master.material.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品主数据版本历史记录：before/after 为字段快照，新建记录 before 为 null。
 * materialId 为雪花 ID，经全局序列化输出为十进制字符串。
 *
 * <p>快照使用独立的对外 {@link Snapshot} 响应结构，不直接暴露领域模型
 * {@code MaterialSnapshot}，避免 Web 契约与领域实现耦合；字段名与历史 JSON 保持一致。
 */
public record MaterialHistoryResponse(
        Long id,
        Long materialId,
        String changeSource,
        String changeType,
        Snapshot before,
        Snapshot after,
        String importBatchNo,
        String remark,
        Long changedBy,
        LocalDateTime changedAt
) {

    /**
     * 商品主数据字段快照响应：只包含业务字段，不含乐观锁版本与审计列。
     */
    public record Snapshot(
            Long id,
            String materialCode,
            String brand,
            String material,
            String category,
            String spec,
            String length,
            String unit,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal unitPrice,
            String remark,
            String materialType
    ) {
    }
}
