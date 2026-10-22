package com.leo.erp.sales.order.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
import com.leo.erp.security.permission.PermissionDecimalSerializer;
import com.leo.erp.security.permission.PermissionField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String orderNo,
        String purchaseInboundNo,
        String purchaseOrderNo,
        String customerCode,
        @JsonSerialize(using = ToStringSerializer.class) Long customerId,
        String customerName,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String projectName,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate deliveryDate,
        /** 报单日期(客户报单/落单当天)。 */
        LocalDate orderDate,
        String salesName,
        BigDecimal totalWeight,
        @PermissionField("sales-orders:read:amount")
        @JsonSerialize(using = PermissionDecimalSerializer.class)
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        @JsonSerialize(using = ToStringSerializer.class) Long priceRuleId,
        String priceRuleName,
        String priceFloatMode,
        BigDecimal priceFloatValue,
        /** 取价基准日(送货日期/报单日期/手工日期)。 */
        LocalDate priceTargetDate,
        /** 实际取价日(交易日历回退后)。 */
        LocalDate priceQuoteDate,
        /** 实际取价时段(上午/中午/下午)。 */
        String priceQuotePeriod,
        /** 本次生效的取价基准: DELIVERY/ORDER/MANUAL。 */
        String priceDateBasis,
        /** 本次生效的偏移交易日数(0..30)。 */
        Integer priceDateOffsetDays,
        /** 本次生效的「基准日休市位移方向」: PREV往前/NEXT延后。 */
        String priceClosedSettleDirection,
        /** 本次生效的「基准日休市位移交易日数」(0..30)。 */
        Integer priceClosedSettleDays,
        List<SalesOrderItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        boolean referencedByFreightBill,
        boolean referencedBySalesOutbound,
        Integer deliveredQuantity,
        Integer returnedQuantity,
        Integer deliveredNetQuantity
) {

    public SalesOrderResponse(Long id,
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
                              List<SalesOrderItemResponse> items,
                              List<DocumentChargeItemResponse> chargeItems) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerId, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, null, salesName, totalWeight,
                totalAmount, status, deletedFlag, remark, null, null, null, null, null, null, null, null, null, null, null,
                items, chargeItems, false, false, null, null, null);
    }

    public SalesOrderResponse withReferenceFlags(boolean referencedByFreightBill,
                                                  boolean referencedBySalesOutbound) {
        return new SalesOrderResponse(
                id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerId, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, orderDate, salesName,
                totalWeight, totalAmount, status, deletedFlag, remark, priceRuleId, priceRuleName, priceFloatMode,
                priceFloatValue, priceTargetDate, priceQuoteDate, priceQuotePeriod, priceDateBasis,
                priceDateOffsetDays, priceClosedSettleDirection, priceClosedSettleDays, items, chargeItems,
                referencedByFreightBill,
                referencedBySalesOutbound, deliveredQuantity, returnedQuantity, deliveredNetQuantity
        );
    }

    public SalesOrderResponse withDerivedQuantities(Integer deliveredQuantity,
                                                     Integer returnedQuantity,
                                                     Integer deliveredNetQuantity) {
        return new SalesOrderResponse(
                id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerId, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, orderDate, salesName,
                totalWeight, totalAmount, status, deletedFlag, remark, priceRuleId, priceRuleName, priceFloatMode,
                priceFloatValue, priceTargetDate, priceQuoteDate, priceQuotePeriod, priceDateBasis,
                priceDateOffsetDays, priceClosedSettleDirection, priceClosedSettleDays, items, chargeItems,
                referencedByFreightBill,
                referencedBySalesOutbound, deliveredQuantity, returnedQuantity, deliveredNetQuantity
        );
    }

    public SalesOrderResponse(Long id,
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
                              List<SalesOrderItemResponse> items) {
        this(id, orderNo, purchaseInboundNo, purchaseOrderNo, customerCode, customerId, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, null, salesName, totalWeight,
                totalAmount, status, deletedFlag, remark, null, null, null, null, null, null, null, null, null,
                null, null, items, List.of(), false, false, null, null, null);
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
