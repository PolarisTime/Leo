package com.leo.erp.sales.order.web.dto;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
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
        /** 交付核定所选价格规定ID(可选); 服务端据此按网价赋价并快照。 */
        Long priceRuleId,
        @Valid @NotEmpty List<SalesOrderItemRequest> items,
        @Valid List<DocumentChargeItemRequest> chargeItems,
        boolean audit
) {

    public SalesOrderRequest {
        if (chargeItems == null) {
            chargeItems = List.of();
        }
    }

    /** 兼容旧调用方: 未携带价格规定。 */
    public SalesOrderRequest(String orderNo,
                             String purchaseInboundNo,
                             String purchaseOrderNo,
                             String customerCode,
                             Long customerId,
                             String customerName,
                             Long projectId,
                             String projectName,
                             Long settlementCompanyId,
                             String settlementCompanyName,
                             LocalDate deliveryDate,
                             String salesName,
                             String status,
                             String remark,
                             List<SalesOrderItemRequest> items,
                             List<DocumentChargeItemRequest> chargeItems,
                             boolean audit) {
        this(orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerId, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, salesName, status, remark,
                null, items, chargeItems, audit);
    }
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
                settlementCompanyId, settlementCompanyName, deliveryDate, salesName, status, remark, null, items,
                List.of(), false);
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
                null, null, deliveryDate, salesName, status, remark, null, items, List.of(), false);
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
                null, null, deliveryDate, salesName, status, remark, null, items, List.of(), false);
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
                null, null, deliveryDate, salesName, status, remark, null, items, List.of(), false);
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
                null, null, deliveryDate, salesName, status, remark, null, items, List.of(), false);
    }

}
