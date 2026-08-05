package com.leo.erp.sales.outbound.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 销售出库明细请求。
 * <p>
 * 明细必须整单从销售订单导入（sourceSalesOrderItemId 必填），后端从销售订单明细重载
 * 材料类字段（品牌/材质/规格/单位等），仅数量/单价/件重等覆盖字段读请求值；其余字段
 * 仅为旧客户端兼容保留。
 */
public record SalesOutboundItemRequest(
        Long id,
        String sourceNo,
        @NotNull(message = "来源销售订单明细不能为空")
        Long sourceSalesOrderItemId,
        Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        Long warehouseId,
        String warehouseName,
        String batchNo,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000") BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        @DecimalMin("0.000") BigDecimal weightTon,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        BigDecimal amount
) {
    public SalesOutboundItemRequest(Long id,
                                    String sourceNo,
                                    Long sourceSalesOrderItemId,
                                    String materialCode,
                                    String brand,
                                    String category,
                                    String material,
                                    String spec,
                                    String length,
                                    String unit,
                                    String warehouseName,
                                    String batchNo,
                                    Integer quantity,
                                    String quantityUnit,
                                    BigDecimal pieceWeightTon,
                                    Integer piecesPerBundle,
                                    BigDecimal weightTon,
                                    BigDecimal unitPrice,
                                    BigDecimal amount) {
        this(id, sourceNo, sourceSalesOrderItemId, null, materialCode, brand, category, material, spec,
                length, unit, null, warehouseName, batchNo, quantity, quantityUnit, pieceWeightTon,
                piecesPerBundle, weightTon, unitPrice, amount);
    }

    public SalesOutboundItemRequest(String sourceNo,
                                    Long sourceSalesOrderItemId,
                                    String materialCode,
                                    String brand,
                                    String category,
                                    String material,
                                    String spec,
                                    String length,
                                    String unit,
                                    String warehouseName,
                                    String batchNo,
                                    Integer quantity,
                                    String quantityUnit,
                                    BigDecimal pieceWeightTon,
                                    Integer piecesPerBundle,
                                    BigDecimal weightTon,
                                    BigDecimal unitPrice,
                                    BigDecimal amount) {
        this(null, sourceNo, sourceSalesOrderItemId, null, materialCode, brand, category, material, spec, length, unit,
                null, warehouseName, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle,
                weightTon, unitPrice, amount);
    }
}
