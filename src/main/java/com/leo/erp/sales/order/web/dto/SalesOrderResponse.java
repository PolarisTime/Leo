package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String orderNo,
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
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesOrderItemResponse> items
) {
    public SalesOrderResponse(Long id,
                              String orderNo,
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
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              boolean deletedFlag,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, null, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, salesName, totalWeight,
                totalAmount, status, deletedFlag, remark, items);
    }

    public SalesOrderResponse(Long id,
                              String orderNo,
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
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, null, customerName, projectId, projectName,
                settlementCompanyId, settlementCompanyName, deliveryDate, salesName, totalWeight, totalAmount,
                status, false, remark, items);
    }

    public SalesOrderResponse(Long id,
                              String orderNo,
                              String purchaseInboundNo,
                              String customerCode,
                              String customerName,
                              Long projectId,
                              String projectName,
                              LocalDate deliveryDate,
                              String salesName,
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, null, customerCode, null, customerName, projectId, projectName, null, null,
                deliveryDate, salesName, totalWeight, totalAmount, status, false, remark, items);
    }

    public SalesOrderResponse(Long id,
                              String orderNo,
                              String purchaseInboundNo,
                              String customerName,
                              String projectName,
                              LocalDate deliveryDate,
                              String salesName,
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, null, null, null, customerName, null, projectName, null, null, deliveryDate,
                salesName, totalWeight, totalAmount, status, false, remark, items);
    }

    public SalesOrderResponse(Long id,
                              String orderNo,
                              String purchaseInboundNo,
                              String purchaseOrderNo,
                              String customerName,
                              String projectName,
                              LocalDate deliveryDate,
                              String salesName,
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, null, null, customerName, null, projectName, null, null,
                deliveryDate, salesName, totalWeight, totalAmount, status, false, remark, items);
    }

    public SalesOrderResponse(Long id,
                              String orderNo,
                              String purchaseInboundNo,
                              String purchaseOrderNo,
                              String customerCode,
                              String customerName,
                              Long projectId,
                              String projectName,
                              LocalDate deliveryDate,
                              String salesName,
                              BigDecimal totalWeight,
                              BigDecimal totalAmount,
                              String status,
                              String remark,
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, null, customerName, projectId,
                projectName, null, null, deliveryDate, salesName, totalWeight, totalAmount, status, false, remark, items);
    }
}
