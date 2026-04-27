package com.leo.erp.master.warehouse.web.dto;

public record WarehouseResponse(
        Long id,
        String warehouseCode,
        String warehouseName,
        String warehouseType,
        String contactName,
        String contactPhone,
        String address,
        String status,
        String remark
) {
}
