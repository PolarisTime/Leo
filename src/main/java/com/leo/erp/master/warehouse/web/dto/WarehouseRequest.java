package com.leo.erp.master.warehouse.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(
        @NotBlank(message = "仓库编码不能为空")
        String warehouseCode,
        @NotBlank(message = "仓库名称不能为空")
        String warehouseName,
        @NotBlank(message = "仓库类型不能为空")
        String warehouseType,
        String contactName,
        String contactPhone,
        String address,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        String status,
        String remark
) {
}
