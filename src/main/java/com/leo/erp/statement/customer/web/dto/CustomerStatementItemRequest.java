package com.leo.erp.statement.customer.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 客户对账单明细请求。
 * <p>
 * 明细必须来自已审核的销售订单出库（整单导入），保存时后端按 sourceSalesOrderItemId
 * 从销售订单明细加载完整字段；其余字段（客户/项目/材料/数量/金额等）仅为旧客户端兼容
 * 保留，不参与保存（后端以库值为准）。
 */
public record CustomerStatementItemRequest(
        Long id,
        String sourceNo,
        @NotNull Long sourceSalesOrderItemId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        String batchNo,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long customerId,
        Long projectId,
        Long materialId,
        Long warehouseId
) {
    public CustomerStatementItemRequest(Long id,
                                        String sourceNo,
                                        Long sourceSalesOrderItemId,
                                        String materialCode,
                                        String brand,
                                        String category,
                                        String material,
                                        String spec,
                                        String length,
                                        String unit,
                                        String batchNo,
                                        Integer quantity,
                                        String quantityUnit,
                                        BigDecimal pieceWeightTon,
                                        Integer piecesPerBundle,
                                        BigDecimal weightTon,
                                        BigDecimal unitPrice,
                                        BigDecimal amount) {
        this(id, sourceNo, sourceSalesOrderItemId, materialCode, brand, category, material, spec, length, unit,
                batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount,
                null, null, null, null);
    }

    public CustomerStatementItemRequest(String sourceNo,
                                        Long sourceSalesOrderItemId,
                                        String materialCode,
                                        String brand,
                                        String category,
                                        String material,
                                        String spec,
                                        String length,
                                        String unit,
                                        String batchNo,
                                        Integer quantity,
                                        String quantityUnit,
                                        BigDecimal pieceWeightTon,
                                        Integer piecesPerBundle,
                                        BigDecimal weightTon,
                                        BigDecimal unitPrice,
                                        BigDecimal amount) {
        this(null, sourceNo, sourceSalesOrderItemId, materialCode, brand, category, material, spec, length, unit, batchNo, quantity,
                quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount, null, null, null, null);
    }
}
