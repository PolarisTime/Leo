package com.leo.erp.master.carrier.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CarrierRequest(
        @NotBlank(message = "物流方编码不能为空")
        String carrierCode,
        @NotBlank(message = "物流方名称不能为空")
        String carrierName,
        @NotBlank(message = "联系人不能为空")
        String contactName,
        @NotBlank(message = "联系电话不能为空")
        String contactPhone,
        @NotBlank(message = "常用车型不能为空")
        String vehicleType,
        String priceMode,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
