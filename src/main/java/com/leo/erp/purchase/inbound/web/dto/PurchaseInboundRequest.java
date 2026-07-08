package com.leo.erp.purchase.inbound.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseInboundRequest(
        String inboundNo,
        String purchaseOrderNo,
        @jakarta.validation.constraints.NotBlank String supplierName,
        String warehouseName,
        @NotNull LocalDate inboundDate,
        String settlementMode,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseInboundItemRequest> items,
        @Valid List<DocumentChargeItemRequest> chargeItems
) {
    public PurchaseInboundRequest(String inboundNo,
                                  String purchaseOrderNo,
                                  String supplierName,
                                  String warehouseName,
                                  LocalDate inboundDate,
                                  String settlementMode,
                                  String status,
                                  String remark,
                                  List<PurchaseInboundItemRequest> items) {
        this(inboundNo, purchaseOrderNo, supplierName, warehouseName, inboundDate,
                settlementMode, status, remark, items, null);
    }
}
