package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class SalesOrderMutationGuardService {

    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;
    private final SalesOrderDownstreamMutationGuard downstreamMutationGuard;
    private final SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;
    private final SalesOrderApplyService salesOrderApplyService;

    public SalesOrderMutationGuardService(SourceAllocationLockService sourceAllocationLockService,
                                          SalesOrderDeliveryVerificationGuard deliveryVerificationGuard,
                                          SalesOrderDownstreamMutationGuard downstreamMutationGuard,
                                          SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
                                          SalesOrderApplyService salesOrderApplyService) {
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.deliveryVerificationGuard = deliveryVerificationGuard;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.protectedUpdatePolicy = protectedUpdatePolicy;
        this.salesOrderApplyService = salesOrderApplyService;
    }

    void lockPurchaseSources(SalesOrder entity, SalesOrderRequest request) {
        Stream<SalesOrderItem> existingItems = entity == null
                ? Stream.empty()
                : entity.getItems().stream();
        List<SalesOrderItem> currentItems = existingItems.toList();
        Stream<SalesOrderItemRequest> requestedItems = request == null
                ? Stream.empty()
                : request.items().stream();
        List<SalesOrderItemRequest> nextItems = requestedItems.toList();
        List<Long> purchaseOrderItemIds = Stream.concat(
                        currentItems.stream().map(SalesOrderItem::getSourcePurchaseOrderItemId),
                        nextItems.stream().map(SalesOrderItemRequest::sourcePurchaseOrderItemId)
                )
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<Long> purchaseInboundItemIds = Stream.concat(
                        currentItems.stream().map(SalesOrderItem::getSourceInboundItemId),
                        nextItems.stream().map(SalesOrderItemRequest::sourceInboundItemId)
                )
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(
                purchaseOrderItemIds,
                purchaseInboundItemIds,
                List.of()
        );
    }

    void assertDeletable(SalesOrder entity) {
        lockPurchaseSources(entity, null);
        downstreamMutationGuard.assertMutable(entity, "删除");
    }

    void assertStatusTransitionAllowed(SalesOrder entity, String currentStatus, String nextStatus) {
        lockPurchaseSources(entity, null);
        if (StatusConstants.SALES_COMPLETED.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "完成销售必须通过专用完成操作执行");
        }
        if (StatusConstants.SALES_COMPLETED.equals(currentStatus)
                && StatusConstants.DELIVERY_VERIFICATION.equals(nextStatus)) {
            deliveryVerificationGuard.assertMutable(entity, "反审核");
        }
        if (StatusConstants.DRAFT.equals(nextStatus)
                && !StatusConstants.DRAFT.equals(currentStatus)) {
            downstreamMutationGuard.assertMutable(entity, "反审核");
        }
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            assertAuditableLineQuantities(entity);
            salesOrderApplyService.validateCustomerSnapshot(entity);
        }
    }

    void assertItemMutationAllowed(SalesOrder entity,
                                   SalesOrderRequest request,
                                   boolean auditedPricingUpdate) {
        if (entity.getItems().stream().anyMatch(item -> item.getId() != null)
                && !auditedPricingUpdate) {
            downstreamMutationGuard.assertNoFreightReference(entity, "修改");
        }
        if (!auditedPricingUpdate
                && entity.getItems().stream().anyMatch(item -> item.getId() != null)) {
            downstreamMutationGuard.assertSourceLineMutationAllowed(entity, request.items(), "修改");
        }
        if (entity.getId() != null
                && StatusConstants.DELIVERY_VERIFICATION.equals(entity.getStatus())) {
            deliveryVerificationGuard.assertMutable(entity, "修改");
        }
    }

    boolean allowsProtectedUpdate(SalesOrder entity, SalesOrderRequest request) {
        return protectedUpdatePolicy.allowsProtectedUpdate(entity, request);
    }

    private void assertAuditableLineQuantities(SalesOrder entity) {
        for (SalesOrderItem item : entity.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行数量必须至少为1个数量单位"
                );
            }
        }
    }
}
