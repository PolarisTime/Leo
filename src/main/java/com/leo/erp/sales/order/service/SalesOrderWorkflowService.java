package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.function.LongSupplier;

@Service
public class SalesOrderWorkflowService {

    private final SalesOrderSaveService saveService;
    private final SalesOrderApplyService salesOrderApplyService;
    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;
    private final SalesOrderMutationGuardService mutationGuardService;
    private final SalesOrderQueryService queryService;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;

    public SalesOrderWorkflowService(SalesOrderSaveService saveService,
                                     SalesOrderApplyService salesOrderApplyService,
                                     SalesOrderAuditedPricingService salesOrderAuditedPricingService,
                                     SalesOrderMutationGuardService mutationGuardService,
                                     SalesOrderQueryService queryService,
                                     BusinessOperationEventPublisher businessOperationEventPublisher) {
        this.saveService = saveService;
        this.salesOrderApplyService = salesOrderApplyService;
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
        this.mutationGuardService = mutationGuardService;
        this.queryService = queryService;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
    }

    public void apply(SalesOrder entity, SalesOrderRequest request, LongSupplier nextIdSupplier) {
        mutationGuardService.lockPurchaseSources(entity, request);
        boolean auditedPricingUpdate = salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request);
        mutationGuardService.assertItemMutationAllowed(entity, request, auditedPricingUpdate);
        if (auditedPricingUpdate) {
            salesOrderApplyService.validateCustomerSnapshot(request);
            salesOrderAuditedPricingService.applyAuditedPricingUpdate(entity, request);
            return;
        }
        salesOrderApplyService.apply(entity, request, nextIdSupplier);
    }

    public SalesOrder save(SalesOrder entity) {
        return saveService.save(entity);
    }

    public SalesOrder saveCreated(SalesOrder entity, SalesOrderRequest request) {
        SalesOrder saved = save(entity);
        publishEvent(saved, "SALES_ORDER_CREATED", "新增", "新增销售订单 " + saved.getOrderNo());
        return saved;
    }

    public SalesOrder saveUpdated(SalesOrder entity, SalesOrderRequest request) {
        SalesOrder saved;
        if (salesOrderAuditedPricingService.isAuditedPricingUpdate(entity, request)) {
            saved = saveService.saveAuditedPricingUpdate(entity);
        } else {
            saved = save(entity);
        }
        publishEvent(saved, "SALES_ORDER_UPDATED", "编辑", "编辑销售订单 " + saved.getOrderNo());
        return saved;
    }

    public SalesOrder saveStatus(SalesOrder entity) {
        return saveService.saveStatus(entity);
    }

    public SalesOrderResponse completeSalesOrder(SalesOrder order) {
        String currentStatus = normalizeStatus(order.getStatus());
        if (StatusConstants.SALES_COMPLETED.equals(currentStatus)) {
            return queryService.toDetailResponse(order);
        }
        if (!StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有交付核定状态可以完成销售");
        }
        salesOrderApplyService.validateCustomerSnapshot(order);
        order.setStatus(StatusConstants.SALES_COMPLETED);
        SalesOrder saved = saveService.saveStatus(order);
        publishEvent(saved, "SALES_ORDER_COMPLETED", "完成销售",
                "销售订单状态 " + currentStatus + " -> " + saved.getStatus());
        return queryService.toDetailResponse(saved);
    }

    public void publishStatusChanged(SalesOrder order, String currentStatus, String nextStatus) {
        publishEvent(order, "SALES_ORDER_STATUS_CHANGED", resolveStatusAction(currentStatus, nextStatus),
                "销售订单状态 " + currentStatus + " -> " + nextStatus);
    }

    public void publishDeleted(SalesOrder entity) {
        publishEvent(entity, "SALES_ORDER_DELETED", "删除", "删除销售订单 " + entity.getOrderNo());
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveStatusAction(String currentStatus, String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            return "审核";
        }
        if (StatusConstants.DRAFT.equals(nextStatus)) {
            return "反审核";
        }
        return "状态变更";
    }

    private void publishEvent(SalesOrder order, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                "sales-order",
                "销售订单",
                actionType,
                "SalesOrder",
                order.getId(),
                order.getOrderNo(),
                remark
        );
    }
}
