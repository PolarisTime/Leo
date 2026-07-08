package com.leo.erp.purchase.inbound.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record PurchaseInboundResponse(
        Long id,
        String inboundNo,
        String purchaseOrderNo,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String warehouseName,
        LocalDate inboundDate,
        String settlementMode,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        String remark,
        BigDecimal totalWeighWeightTon,
        BigDecimal totalWeightAdjustmentTon,
        List<PurchaseInboundItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        BigDecimal totalChargeAmount,
        BigDecimal payableAmount
) {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public PurchaseInboundResponse(Long id,
                                   String inboundNo,
                                   String purchaseOrderNo,
                                   String supplierName,
                                   Long settlementCompanyId,
                                   String settlementCompanyName,
                                   String warehouseName,
                                   LocalDate inboundDate,
                                   String settlementMode,
                                   BigDecimal totalWeight,
                                   BigDecimal totalAmount,
                                   String status,
                                   String remark,
                                   BigDecimal totalWeighWeightTon,
                                   BigDecimal totalWeightAdjustmentTon,
                                   List<PurchaseInboundItemResponse> items) {
        this(id, inboundNo, purchaseOrderNo, supplierName, settlementCompanyId, settlementCompanyName,
                warehouseName, inboundDate, settlementMode, totalWeight, totalAmount, status, remark,
                totalWeighWeightTon, totalWeightAdjustmentTon, items, List.of(), ZERO_AMOUNT, totalAmount);
    }

    public PurchaseInboundResponse(Long id,
                                   String inboundNo,
                                   String purchaseOrderNo,
                                   String supplierName,
                                   String warehouseName,
                                   LocalDate inboundDate,
                                   String settlementMode,
                                   BigDecimal totalWeight,
                                   BigDecimal totalAmount,
                                   String status,
                                   String remark,
                                   BigDecimal totalWeighWeightTon,
                                   BigDecimal totalWeightAdjustmentTon,
                                   List<PurchaseInboundItemResponse> items) {
        this(id, inboundNo, purchaseOrderNo, supplierName, null, null, warehouseName, inboundDate,
                settlementMode, totalWeight, totalAmount, status, remark, totalWeighWeightTon,
                totalWeightAdjustmentTon, items);
    }
}
