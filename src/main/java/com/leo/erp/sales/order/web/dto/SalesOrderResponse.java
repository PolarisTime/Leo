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
        Integer deliveredQuantity,
        Integer returnedQuantity,
        Integer deliveredNetQuantity,
        /** 项目简称(取自 md_project.project_name_abbr); 项目缺失/已删除/简称为空 → null, 由前端回退显示全称。 */
        String projectNameAbbr
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
                items, chargeItems, null, null, null, null);
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
                deliveredQuantity, returnedQuantity, deliveredNetQuantity, projectNameAbbr
        );
    }

    /**
     * 补「项目简称」: 由查询侧 {@code ProjectNameAbbrResolver} 统一回填, 不参与 MapStruct 映射。
     *
     * <p>刻意声明为 static: 单参实例方法会被 MapStruct 识别为"流式 setter"并要求提供映射目标,
     * 而这个字段恰恰只能由查询侧填, 映射层必须忽略它。</p>
     */
    public static SalesOrderResponse withProjectNameAbbr(SalesOrderResponse source, String projectNameAbbr) {
        return new SalesOrderResponse(
                source.id(), source.orderNo(), source.purchaseInboundNo(), source.purchaseOrderNo(),
                source.customerCode(), source.customerId(), source.customerName(), source.projectId(),
                source.projectName(), source.settlementCompanyId(), source.settlementCompanyName(),
                source.deliveryDate(), source.orderDate(), source.salesName(), source.totalWeight(),
                source.totalAmount(), source.status(), source.deletedFlag(), source.remark(), source.priceRuleId(),
                source.priceRuleName(), source.priceFloatMode(), source.priceFloatValue(), source.priceTargetDate(),
                source.priceQuoteDate(), source.priceQuotePeriod(), source.priceDateBasis(),
                source.priceDateOffsetDays(), source.priceClosedSettleDirection(), source.priceClosedSettleDays(),
                source.items(), source.chargeItems(), source.deliveredQuantity(), source.returnedQuantity(),
                source.deliveredNetQuantity(), projectNameAbbr
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
                null, null, items, List.of(), null, null, null, null);
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
