package com.leo.erp.statement.freight.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 物流对账单明细请求。
 * <p>
 * 明细必须来自已审核的物流单（整单导入），保存时后端按 sourceFreightBillId /
 * sourceFreightBillItemId 从物流单明细加载完整字段；其余字段（客户/项目/材料/重量等）
 * 仅为旧客户端兼容保留，不参与保存（后端以库值为准）。
 */
public record FreightStatementItemRequest(
        Long id,
        String sourceNo,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long materialId,
        String materialCode,
        String materialName,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        String batchNo,
        BigDecimal weightTon,
        Long warehouseId,
        String warehouseName,
        @NotNull Long sourceFreightBillId,
        @NotNull Long sourceFreightBillItemId,
        Long sourceSalesOrderItemId
) {
}
