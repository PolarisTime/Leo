package com.leo.erp.sales.outbound.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOutboundResponse(
        Long id,
        String outboundNo,
        String salesOrderNo,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long warehouseId,
        String warehouseName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate outboundDate,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOutboundItemResponse> items,
        Long sourceFreightBillId
) {
    public SalesOutboundResponse(Long id,
                                 String outboundNo,
                                 String salesOrderNo,
                                 Long customerId,
                                 String customerName,
                                 Long projectId,
                                 String projectName,
                                 Long warehouseId,
                                 String warehouseName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 LocalDate outboundDate,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<SalesOutboundItemResponse> items) {
        this(id, outboundNo, salesOrderNo, customerId, customerName, projectId, projectName, warehouseId,
                warehouseName, settlementCompanyId, settlementCompanyName, outboundDate, totalWeight, totalAmount,
                status, deletedFlag, remark, items, null);
    }

    public SalesOutboundResponse(Long id,
                                 String outboundNo,
                                 String salesOrderNo,
                                 String customerName,
                                 String projectName,
                                 String warehouseName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 LocalDate outboundDate,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<SalesOutboundItemResponse> items) {
        this(id, outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName,
                settlementCompanyId, settlementCompanyName, outboundDate, totalWeight, totalAmount,
                status, deletedFlag, remark, items);
    }

    public SalesOutboundResponse(Long id,
                                 String outboundNo,
                                 String salesOrderNo,
                                 String customerName,
                                 String projectName,
                                 String warehouseName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 LocalDate outboundDate,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<SalesOutboundItemResponse> items) {
        this(id, outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName,
                settlementCompanyId, settlementCompanyName,
                outboundDate, totalWeight, totalAmount, status, false, remark, items);
    }

    public SalesOutboundResponse(Long id,
                                 String outboundNo,
                                 String salesOrderNo,
                                 String customerName,
                                 String projectName,
                                 String warehouseName,
                                 LocalDate outboundDate,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<SalesOutboundItemResponse> items) {
        this(id, outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName, null, null,
                outboundDate, totalWeight, totalAmount, status, false, remark, items);
    }
}
