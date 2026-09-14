package com.leo.erp.sales.returns.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

public record SalesReturnItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        Integer lineNo,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceSalesOutboundItemId,
        String sourceSalesOutboundNo,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceSalesOrderItemId,
        String sourceSalesOrderNo,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceFreightBillId,
        String sourceFreightBillNo,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        String batchNo,
        String batchNoNormalized,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
