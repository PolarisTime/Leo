package com.leo.erp.purchase.inbound.web.dto;

import java.math.BigDecimal;
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
        List<PurchaseInboundItemResponse> items
) {
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
