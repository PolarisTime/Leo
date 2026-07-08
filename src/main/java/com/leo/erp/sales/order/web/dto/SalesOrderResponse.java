package com.leo.erp.sales.order.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id,
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
        List<SalesOrderItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        BigDecimal totalChargeAmount,
        BigDecimal receivableAmount
) {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, salesName,
                totalWeight, totalAmount, status, remark, items, List.of(), ZERO_AMOUNT, totalAmount);
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
        this(id, orderNo, purchaseInboundNo, null, customerCode, customerName, projectId, projectName, deliveryDate, salesName,
                totalWeight, totalAmount, status, remark, items);
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
        this(id, orderNo, purchaseInboundNo, null, null, customerName, null, projectName, null, null, deliveryDate,
                salesName, totalWeight, totalAmount, status, remark, items);
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
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, null, customerName, null, projectName, null, null,
                deliveryDate, salesName, totalWeight, totalAmount, status, remark, items);
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
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerName, projectId,
                projectName, null, null, deliveryDate, salesName, totalWeight, totalAmount, status, remark, items);
    }
}
