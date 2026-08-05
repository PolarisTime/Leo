package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 物流单明细请求。
 * <p>
 * 明细必须整单从销售订单导入（后端校验来源明细集合必须等于销售订单全部明细），
 * 保存时后端按 sourceSalesOrderItemId 从销售订单明细加载完整字段；其余字段仅为旧
 * 客户端兼容保留，不参与保存（后端以来源明细为准）。
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
