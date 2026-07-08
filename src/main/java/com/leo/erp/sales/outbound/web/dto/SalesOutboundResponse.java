package com.leo.erp.sales.outbound.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record SalesOutboundResponse(
        Long id,
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
        List<SalesOutboundItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        BigDecimal totalChargeAmount,
        BigDecimal receivableAmount
) {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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
        this(id, outboundNo, salesOrderNo, customerName, projectName, warehouseName,
                settlementCompanyId, settlementCompanyName, outboundDate, totalWeight, totalAmount,
                status, remark, items, List.of(), ZERO_AMOUNT, totalAmount);
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
        this(id, outboundNo, salesOrderNo, customerName, projectName, warehouseName, null, null,
                outboundDate, totalWeight, totalAmount, status, remark, items);
    }
}
