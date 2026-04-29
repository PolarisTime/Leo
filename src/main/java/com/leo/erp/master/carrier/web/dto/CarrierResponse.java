package com.leo.erp.master.carrier.web.dto;

public record CarrierResponse(
        Long id,
        String carrierCode,
        String carrierName,
        String contactName,
        String contactPhone,
        String vehicleType,
        String vehiclePlates,
        String priceMode,
        String status,
        String remark
) {
}
