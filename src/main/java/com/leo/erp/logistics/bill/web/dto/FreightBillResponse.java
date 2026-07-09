package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
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
        List<FreightBillItemResponse> items
) {
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
