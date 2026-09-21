package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 物流单明细请求。
 * <p>
 * 明细按 {@code sourceSalesOrderItemId} 从销售订单行级导入，允许部分导入/拆分：
 * 请求行必须是所选销售订单明细的子集，且单内不允许重复引用同一来源行。
 * 品牌/规格/仓库/件重等固定字段以来源明细为准（显式提供时必须与来源一致）；
 * {@code quantity} 缺省时自动引用来源快照数量，显式给出则以请求为准（不得超过来源剩余额度）；
 * {@code weightTon} 显式给出且大于 0 时按过磅重量采用，否则按「来源件重 × 数量」换算。
 */
public record FreightBillItemRequest(
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
        Long sourceFreightBillId,
        Long sourceFreightBillItemId,
        @NotNull Long sourceSalesOrderItemId
) {
}
