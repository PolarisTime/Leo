package com.leo.erp.sales.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesOrderRequest(
        String orderNo,
        String purchaseInboundNo,
        String purchaseOrderNo,
        String customerCode,
        @jakarta.validation.constraints.Positive Long customerId,
        @jakarta.validation.constraints.NotBlank String customerName,
        Long projectId,
        @jakarta.validation.constraints.NotBlank String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotNull LocalDate deliveryDate,
        @jakarta.validation.constraints.NotBlank String salesName,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesOrderItemRequest> items
) {
    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String purchaseOrderNo,
                             String customerCode,
                             String customerName,
                             Long projectId,
                             String projectName,
                             Long settlementCompanyId,
                             String settlementCompanyName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items) {
        this(orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, null, customerName, projectId, projectName,
                settlementCompanyId, settlementCompanyName, deliveryDate, salesName, status, remark, items);
    }

    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String purchaseOrderNo,
                             String customerCode,
                             String customerName,
                             Long projectId,
                             String projectName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items) {
        this(orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, null, customerName, projectId, projectName,
                null, null, deliveryDate, salesName, status, remark, items);
    }

    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String customerCode,
                             String customerName,
                             Long projectId,
                             String projectName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items) {
        this(orderNo, purchaseInboundNo, null, customerCode, null, customerName, projectId, projectName,
                null, null, deliveryDate, salesName, status, remark, items);
    }

    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String customerName,
                             String projectName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items) {
        this(orderNo, purchaseInboundNo, null, null, null, customerName, null, projectName,
                null, null, deliveryDate, salesName, status, remark, items);
    }

    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String purchaseOrderNo,
                             String customerName,
                             String projectName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items) {
        this(orderNo, purchaseInboundNo, purchaseOrderNo, null, null, customerName, null, projectName,
                null, null, deliveryDate, salesName, status, remark, items);
    }
}
