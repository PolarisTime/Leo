package com.leo.erp.inventory.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.leo.erp.security.permission.PermissionDecimalSerializer;
import com.leo.erp.security.permission.PermissionField;

import java.math.BigDecimal;

/**
 * 库存余额（按 material / warehouse / batch 聚合）。
 */
public record InventoryBalanceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        String materialCode,
        String brand,
        String material,
        String spec,
        String length,
        String unit,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        String batchNo,
        long quantity,
        @PermissionField("inventory:read:cost")
        @JsonSerialize(using = PermissionDecimalSerializer.class)
        BigDecimal amount,
        @PermissionField("inventory:read:cost")
        @JsonSerialize(using = PermissionDecimalSerializer.class)
        BigDecimal avgUnitCost
) {
}
