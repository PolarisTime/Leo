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
        /** 报单日期(客户报单/落单当天); 取价基准 ORDER 的锚点, 缺省时保留原值/落到当天。 */
        LocalDate orderDate,
        @jakarta.validation.constraints.NotBlank String salesName,
        String status,
        String remark,
        /** 交付核定所选价格规定ID(可选); 服务端据此按网价赋价并快照。 */
        Long priceRuleId,
        /** 取价基准日(送货日期/报单日期/手工日期); 交付核定解析结果快照。 */
        LocalDate priceTargetDate,
        /** 实际取价日(交易日历回退后)。 */
        LocalDate priceQuoteDate,
        /** 实际取价时段(上午/中午/下午)。 */
        String priceQuotePeriod,
        /** 本次生效的取价基准: DELIVERY/ORDER/MANUAL。 */
        String priceDateBasis,
        /** 本次生效的偏移交易日数(0..30)。 */
        Integer priceDateOffsetDays,
        /** 本次生效的「报单日休市按前一交易日结算」开关。 */
        Boolean priceClosedSettlePrev,
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
                projectName, settlementCompanyId, settlementCompanyName, deliveryDate, null, salesName, status, remark,
                null, null, null, null, null, null, null, items, chargeItems, audit);
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
                settlementCompanyId, settlementCompanyName, deliveryDate, null, salesName, status, remark, null, null, null, null, null, null, null, items,
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
                null, null, deliveryDate, null, salesName, status, remark, null, null, null, null, null, null, null, items, List.of(), false);
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
                null, null, deliveryDate, null, salesName, status, remark, null, null, null, null, null, null, null, items, List.of(), false);
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
                null, null, deliveryDate, null, salesName, status, remark, null, null, null, null, null, null, null, items, List.of(), false);
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
                null, null, deliveryDate, null, salesName, status, remark, null, null, null, null, null, null, null, items, List.of(), false);
    }

}
