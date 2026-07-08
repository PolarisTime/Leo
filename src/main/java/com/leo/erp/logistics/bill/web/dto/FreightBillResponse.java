package com.leo.erp.logistics.bill.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record FreightBillResponse(
        Long id,
        String billNo,
        String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String vehiclePlate,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        String remark,
        List<FreightBillItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        BigDecimal totalChargeAmount,
        BigDecimal payableAmount
) {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public FreightBillResponse(Long id,
                               String billNo,
                               String carrierName,
                               Long settlementCompanyId,
                               String settlementCompanyName,
                               String vehiclePlate,
                               String customerName,
                               String projectName,
                               LocalDate billTime,
                               BigDecimal unitPrice,
                               BigDecimal totalWeight,
                               BigDecimal totalFreight,
                               String status,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, carrierName, settlementCompanyId, settlementCompanyName, vehiclePlate,
                customerName, projectName, billTime, unitPrice, totalWeight, totalFreight,
                status, remark, items, List.of(), ZERO_AMOUNT, totalFreight);
    }

    public FreightBillResponse(Long id,
                               String billNo,
                               String carrierName,
                               String vehiclePlate,
                               String customerName,
                               String projectName,
                               LocalDate billTime,
                               BigDecimal unitPrice,
                               BigDecimal totalWeight,
                               BigDecimal totalFreight,
                               String status,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, carrierName, null, null, vehiclePlate, customerName, projectName, billTime,
                unitPrice, totalWeight, totalFreight, status, remark, items);
    }
}
