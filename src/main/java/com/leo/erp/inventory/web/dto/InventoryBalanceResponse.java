package com.leo.erp.inventory.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 库存余额（按 material / warehouse / batch 聚合）。
 */
public record InventoryBalanceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        String materialCode,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        String batchNo,
        long quantity,
        BigDecimal amount,
        BigDecimal avgUnitCost
) {
}
