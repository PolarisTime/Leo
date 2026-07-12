package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillRequest(
        String billNo,
        Long carrierId,
        String carrierCode,
        @jakarta.validation.constraints.NotBlank String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long vehicleId,
        String vehiclePlate,
        @jakarta.validation.constraints.NotBlank String customerName,
        @jakarta.validation.constraints.NotBlank String projectName,
        @NotNull LocalDate billTime,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        String status,
        String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items
) {
    public FreightBillRequest(String billNo,
                              String carrierCode,
                              String carrierName,
                              Long settlementCompanyId,
                              String settlementCompanyName,
                              String vehiclePlate,
                              String customerName,
                              String projectName,
                              LocalDate billTime,
                              BigDecimal unitPrice,
                              String status,
                              String remark,
                              List<FreightBillItemRequest> items) {
        this(billNo, null, carrierCode, carrierName, settlementCompanyId, settlementCompanyName, null, vehiclePlate,
                customerName, projectName, billTime, unitPrice, status, remark, items);
    }

    public FreightBillRequest(String billNo,
                              String carrierCode,
                              String carrierName,
                              String vehiclePlate,
                              String customerName,
                              String projectName,
                              LocalDate billTime,
                              BigDecimal unitPrice,
                              String status,
                              String remark,
                              List<FreightBillItemRequest> items) {
        this(billNo, null, carrierCode, carrierName, null, null, null, vehiclePlate, customerName, projectName,
                billTime, unitPrice, status, remark, items);
    }

    public FreightBillRequest(String billNo,
                              String carrierName,
                              Long settlementCompanyId,
                              String settlementCompanyName,
                              String vehiclePlate,
                              String customerName,
                              String projectName,
                              LocalDate billTime,
                              BigDecimal unitPrice,
                              String status,
                              String remark,
                              List<FreightBillItemRequest> items) {
        this(billNo, null, null, carrierName, settlementCompanyId, settlementCompanyName, null, vehiclePlate,
                customerName, projectName, billTime, unitPrice, status, remark, items);
    }

    public FreightBillRequest(String billNo,
                              String carrierName,
                              String vehiclePlate,
                              String customerName,
                              String projectName,
                              LocalDate billTime,
                              BigDecimal unitPrice,
                              String status,
                              String remark,
                              List<FreightBillItemRequest> items) {
        this(billNo, null, null, carrierName, null, null, null, vehiclePlate, customerName, projectName, billTime,
                unitPrice, status, remark, items);
    }
}
