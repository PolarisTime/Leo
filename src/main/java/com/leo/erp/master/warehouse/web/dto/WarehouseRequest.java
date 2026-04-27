package com.leo.erp.master.warehouse.web.dto;

import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(
        @NotBlank(message = "仓库编码不能为空")
        String warehouseCode,
        @NotBlank(message = "仓库名称不能为空")
        String warehouseName,
        @NotBlank(message = "仓库类型不能为空")
        String warehouseType,
        @NotBlank(message = "联系人不能为空")
        String contactName,
        @NotBlank(message = "联系电话不能为空")
        String contactPhone,
        @NotBlank(message = "仓库地址不能为空")
        String address,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
