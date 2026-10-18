package com.leo.erp.master.warehouse.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank(message = "仓库编码不能为空")
        @Size(max = 64, message = "仓库编码长度不能超过64")
        String warehouseCode,
        @NotBlank(message = "仓库名称不能为空")
        @Size(max = 128, message = "仓库名称长度不能超过128")
        String warehouseName,
        @NotBlank(message = "仓库类型不能为空")
        @Size(max = 32, message = "仓库类型长度不能超过32")
        String warehouseType,
        @Size(max = 32, message = "联系人长度不能超过32")
        String contactName,
        @Size(max = 20, message = "联系电话长度不能超过20")
        String contactPhone,
        @Size(max = 255, message = "地址长度不能超过255")
        String address,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 16, message = "状态长度不能超过16")
        String status,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
