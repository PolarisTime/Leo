package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id,
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
        List<SalesOrderItemResponse> items
) {
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
        this(id, orderNo, purchaseInboundNo, null, customerName, projectName, deliveryDate, salesName,
                totalWeight, totalAmount, status, remark, items);
    }
}
