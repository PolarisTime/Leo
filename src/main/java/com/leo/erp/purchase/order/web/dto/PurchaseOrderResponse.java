package com.leo.erp.purchase.order.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leo.erp.common.charge.api.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDateTime orderDate,
        String buyerName,
        Long settlementCompanyId,
        String settlementCompanyName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<PurchaseOrderItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        boolean referencedBySalesOrder,
        boolean referencedByPurchaseInbound,
        Integer totalRemainingQuantity,
        BigDecimal totalActualAmount,
        BigDecimal totalAmountDifference,
        Integer totalReceivedQuantity,
        /**
         * 强制结单留痕; 未强制结单时为 null。
         *
         * <p>必须 {@code NON_NULL}: Jackson 默认会把 record 的空组件序列化为 {@code "forceClose": null},
         * 而线上前端对采购订单列表用的是 {@code z.strictObject}, 多出的键会让整页解析失败。
         * 未强制结单时不输出该键, 以保持与旧前端的向后兼容(同 chargeItems:null 需要显式声明的道理)。</p>
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ForceCloseInfo forceClose
) {

    /**
     * 强制结单留痕: 剩余未入库件数作废后由人工把订单置为完成采购。
     *
     * @param reason            结单原因(必填)
     * @param remainingQuantity 结单时未入库件数快照(即本次作废件数)
     * @param operatorId        操作人ID
     * @param operatorName      操作人姓名快照
     * @param closedAt          结单时刻
     */
    public record ForceCloseInfo(
            String reason,
            Integer remainingQuantity,
            Long operatorId,
            String operatorName,
            LocalDateTime closedAt
    ) {
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 Long supplierId,
                                 String supplierCode,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items,
                                 List<DocumentChargeItemResponse> chargeItems) {
        this(id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                false, false, null, null, null, null, null);
    }

    public PurchaseOrderResponse withReferenceFlags(boolean referencedBySalesOrder,
                                                     boolean referencedByPurchaseInbound) {
        return new PurchaseOrderResponse(
                id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                referencedBySalesOrder, referencedByPurchaseInbound, totalRemainingQuantity,
                totalActualAmount, totalAmountDifference, totalReceivedQuantity, forceClose
        );
    }

    /** 覆盖强制结单留痕(实体派生), 其余字段保持不变。 */
    public PurchaseOrderResponse withForceClose(ForceCloseInfo forceClose) {
        return new PurchaseOrderResponse(
                id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                referencedBySalesOrder, referencedByPurchaseInbound, totalRemainingQuantity,
                totalActualAmount, totalAmountDifference, totalReceivedQuantity, forceClose
        );
    }

    public PurchaseOrderResponse applyTotalRemainingQuantity(Integer totalRemainingQuantity) {
        return new PurchaseOrderResponse(
                id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                referencedBySalesOrder, referencedByPurchaseInbound, totalRemainingQuantity,
                totalActualAmount, totalAmountDifference, totalReceivedQuantity, forceClose
        );
    }

    public PurchaseOrderResponse applyDifference(BigDecimal totalActualAmount, BigDecimal totalAmountDifference) {
        return new PurchaseOrderResponse(
                id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                referencedBySalesOrder, referencedByPurchaseInbound, totalRemainingQuantity,
                totalActualAmount, totalAmountDifference, totalReceivedQuantity, forceClose
        );
    }

    public PurchaseOrderResponse applyReceivedQuantity(Integer totalReceivedQuantity) {
        return new PurchaseOrderResponse(
                id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, chargeItems,
                referencedBySalesOrder, referencedByPurchaseInbound, totalRemainingQuantity,
                totalActualAmount, totalAmountDifference, totalReceivedQuantity, forceClose
        );
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 Long supplierId,
                                 String supplierCode,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(id, orderNo, supplierId, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, List.of(),
                false, false, null, null, null, null, null);
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierCode,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(id, orderNo, null, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, List.of());
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(id, orderNo, null, null, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items, List.of());
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(
                id,
                orderNo,
                null,
                null,
                supplierName,
                orderDate,
                buyerName,
                settlementCompanyId,
                settlementCompanyName,
                totalWeight,
                totalAmount,
                status,
                false,
                remark,
                items
        );
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(
                id,
                orderNo,
                null,
                null,
                supplierName,
                orderDate,
                buyerName,
                null,
                null,
                totalWeight,
                totalAmount,
                status,
                false,
                remark,
                items
        );
    }
}
