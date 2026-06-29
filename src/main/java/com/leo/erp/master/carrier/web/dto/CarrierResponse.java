package com.leo.erp.master.carrier.web.dto;

import java.util.List;

public record CarrierResponse(
        Long id,
        String carrierCode,
        String carrierName,
        String contactName,
        String contactPhone,
        String vehicleType,
        List<VehicleInfo> vehicles,
        String priceMode,
        Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName,
        String status,
        String remark
) {
    public CarrierResponse(Long id,
                           String carrierCode,
                           String carrierName,
                           String contactName,
                           String contactPhone,
                           String vehicleType,
                           List<VehicleInfo> vehicles,
                           String priceMode,
                           String status,
                           String remark) {
        this(
                id,
                carrierCode,
                carrierName,
                contactName,
                contactPhone,
                vehicleType,
                vehicles,
                priceMode,
                null,
                null,
                status,
                remark
        );
    }
}
