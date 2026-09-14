package com.leo.erp.inventory.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存事务流水分页项。
 */
public record InventoryTransactionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String transactionNo,
        String transactionType,
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        String materialCode,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        String batchNo,
        Short direction,
        Integer quantity,
        String quantityUnit,
        BigDecimal unitCost,
        BigDecimal amount,
        String sourceDocumentType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceDocumentId,
        String sourceDocumentNo,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceItemId,
        LocalDate occurredAt,
        LocalDateTime createdAt
) {
}
