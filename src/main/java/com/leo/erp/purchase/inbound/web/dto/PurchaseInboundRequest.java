package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseInboundRequest(
        String inboundNo,
        @NotBlank String purchaseOrderNo,
        @NotNull Long supplierId,
        @NotBlank String supplierCode,
        @jakarta.validation.constraints.NotBlank String supplierName,
        @NotNull Long warehouseId,
        @NotBlank String warehouseName,
        @NotNull LocalDate inboundDate,
        String settlementMode,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseInboundItemRequest> items
) {
    public PurchaseInboundRequest(String inboundNo,
                                  String purchaseOrderNo,
                                  String supplierCode,
                                  String supplierName,
                                  String warehouseName,
                                  LocalDate inboundDate,
                                  String settlementMode,
                                  String status,
                                  String remark,
                                  List<PurchaseInboundItemRequest> items) {
        this(inboundNo, purchaseOrderNo, null, supplierCode, supplierName, null, warehouseName, inboundDate,
                settlementMode, status, remark, items);
    }

    public PurchaseInboundRequest(String inboundNo,
                                  String purchaseOrderNo,
                                  String supplierName,
                                  String warehouseName,
                                  LocalDate inboundDate,
                                  String settlementMode,
                                  String status,
                                  String remark,
                                  List<PurchaseInboundItemRequest> items) {
        this(inboundNo, purchaseOrderNo, null, null, supplierName, null, warehouseName, inboundDate,
                settlementMode, status, remark, items);
    }
}
