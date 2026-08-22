package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.util.List;

public record FreightBillResponse(
        Long id,
        String billNo,
        Long carrierId,
        String carrierCode,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long vehicleId,
        String vehiclePlate,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        boolean deletedFlag,
        String remark,
        List<FreightBillItemResponse> items,
        BigDecimal totalExpenseAmount,
        List<DocumentChargeItemResponse> chargeItems
) {

    public FreightBillResponse(Long id,
                               String billNo,
                               Long carrierId,
                               String carrierCode,
                               String carrierName,
                               Long settlementCompanyId,
                               String settlementCompanyName,
                               Long vehicleId,
                               String vehiclePlate,
                               LocalDate billTime,
                               BigDecimal unitPrice,
                               BigDecimal totalWeight,
                               BigDecimal totalFreight,
                               String status,
                               boolean deletedFlag,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, carrierId, carrierCode, carrierName, settlementCompanyId, settlementCompanyName,
                vehicleId, vehiclePlate, billTime, unitPrice, totalWeight, totalFreight, status, deletedFlag,
                remark, items, null, List.of());
    }
}
