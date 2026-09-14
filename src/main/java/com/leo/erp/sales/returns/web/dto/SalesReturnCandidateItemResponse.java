package com.leo.erp.sales.returns.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

public record SalesReturnCandidateItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long sourceSalesOutboundItemId,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceSalesOrderItemId,
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
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        Integer outboundQuantity,
        Integer returnedQuantity,
        Integer returnableQuantity,
        BigDecimal unitPrice
) {
}
