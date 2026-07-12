package com.leo.erp.logistics.bill.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal unitPrice,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status,
        boolean deletedFlag,
        String remark,
        List<FreightBillItemResponse> items
) {
    public FreightBillResponse(Long id,
                               String billNo,
                               String carrierCode,
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
                               boolean deletedFlag,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, null, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, null,
                vehiclePlate, customerName, projectName, billTime, unitPrice, totalWeight, totalFreight, status,
                deletedFlag, remark, items);
    }

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
                               boolean deletedFlag,
                               String remark,
                               List<FreightBillItemResponse> items) {
        this(id, billNo, null, null, carrierName, settlementCompanyId, settlementCompanyName, null, vehiclePlate,
                customerName, projectName, billTime, unitPrice, totalWeight, totalFreight, status,
                deletedFlag, remark, items);
    }

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
        this(id, billNo, null, null, carrierName, settlementCompanyId, settlementCompanyName, null, vehiclePlate,
                customerName,
                projectName, billTime, unitPrice, totalWeight, totalFreight, status, false, remark, items);
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
        this(id, billNo, null, null, carrierName, null, null, null, vehiclePlate, customerName, projectName, billTime,
                unitPrice, totalWeight, totalFreight, status, false, remark, items);
    }
}
