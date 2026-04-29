package com.leo.erp.master.carrier.web.dto;

public record CarrierResponse(
        Long id,
        String carrierCode,
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
        String status,
        String remark
) {
}
