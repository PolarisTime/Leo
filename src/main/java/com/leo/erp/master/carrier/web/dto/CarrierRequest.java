package com.leo.erp.master.carrier.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CarrierRequest(
        @NotBlank(message = "物流方编码不能为空")
        String carrierCode,
        @NotBlank(message = "物流方名称不能为空")
        String carrierName,
        String contactName,
        String contactPhone,
        String vehicleType,
        String vehiclePlates,
        String vehiclePlate,
        String vehicleContact,
        String vehiclePhone,
        String vehiclePlate2,
        String vehicleContact2,
        String vehiclePhone2,
        String vehiclePlate3,
        String vehicleContact3,
        String vehiclePhone3,
        String vehicleRemark,
        String vehicleRemark2,
        String vehicleRemark3,
        String priceMode,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
