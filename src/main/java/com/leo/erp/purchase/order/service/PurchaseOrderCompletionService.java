package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SupplierLedgerLockService;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderCompletionResponse;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderCompletionService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.PURCHASE_COMPLETED
    );
    private static final Set<String> EFFECTIVE_INBOUND_STATUSES = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.INBOUND_COMPLETED
    );

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseInboundItemRepository inboundItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SupplierLedgerLockService supplierLedgerLockService;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public PurchaseOrderCompletionService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseInboundItemRepository inboundItemRepository,
            SourceAllocationLockService sourceAllocationLockService,
            ResourceRecordAccessGuard resourceRecordAccessGuard,
            WorkflowTransitionGuard workflowTransitionGuard,
            SupplierLedgerLockService supplierLedgerLockService,
            SalesOrderItemRepository salesOrderItemRepository
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.inboundItemRepository = inboundItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.supplierLedgerLockService = supplierLedgerLockService;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    @Transactional
    public PurchaseOrderCompletionResponse completePurchaseOrder(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购订单不能为空");
        }
        PurchaseOrder purchaseOrder = purchaseOrderRepository
                .findByIdAndDeletedFlagFalseForUpdate(purchaseOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在"));
        resourceRecordAccessGuard.assertCurrentUserCanAccess("purchase-order", "read", purchaseOrder);
        if (!ALLOWED_STATUSES.contains(purchaseOrder.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有已审核采购订单可以完成采购");
        }
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-order",
                purchaseOrder.getStatus(),
                StatusConstants.PURCHASE_COMPLETED,
                StatusConstants.PURCHASE_COMPLETED
        );

        List<Long> sourceItemIds = purchaseOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(sourceItemIds, List.of(), List.of());
        List<PurchaseInboundItem> inboundItems = sourceItemIds.isEmpty()
                ? List.of()
                : inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds);
        assertNoDraftInbound(inboundItems);
        Map<Long, Long> inboundQuantityBySourceItemId = effectiveInboundQuantityBySourceItemId(inboundItems);
        assertFullyInbound(purchaseOrder, inboundQuantityBySourceItemId);
        assertPresaleCapacityCovered(sourceItemIds, inboundQuantityBySourceItemId);

        if (!StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            lockSupplierLedger(purchaseOrder);
            purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
            purchaseOrderRepository.save(purchaseOrder);
        }
        return new PurchaseOrderCompletionResponse(
                purchaseOrder.getId(),
                purchaseOrder.getOrderNo(),
                purchaseOrder.getStatus()
        );
    }

    private void assertNoDraftInbound(List<PurchaseInboundItem> inboundItems) {
        boolean hasDraftInbound = inboundItems.stream()
                .map(PurchaseInboundItem::getPurchaseInbound)
                .filter(Objects::nonNull)
                .anyMatch(inbound -> StatusConstants.DRAFT.equals(inbound.getStatus()));
        if (hasDraftInbound) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单存在草稿采购入库单，完成采购前必须先审核或删除"
            );
        }
    }

    private Map<Long, Long> effectiveInboundQuantityBySourceItemId(List<PurchaseInboundItem> inboundItems) {
        return inboundItems.stream()
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .filter(item -> item.getPurchaseInbound() != null)
                .filter(item -> EFFECTIVE_INBOUND_STATUSES.contains(item.getPurchaseInbound().getStatus()))
                .collect(Collectors.groupingBy(
                        PurchaseInboundItem::getSourcePurchaseOrderItemId,
                        Collectors.summingLong(item -> item.getQuantity() == null ? 0L : item.getQuantity())
                ));
    }

    private void assertFullyInbound(
            PurchaseOrder purchaseOrder,
            Map<Long, Long> inboundQuantityBySourceItemId
    ) {
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            long orderedQuantity = item.getQuantity() == null ? 0L : item.getQuantity();
            long inboundQuantity = inboundQuantityBySourceItemId.getOrDefault(item.getId(), 0L);
            if (orderedQuantity < 1 || inboundQuantity != orderedQuantity) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "采购订单必须全部商品完成入库后才能完成采购"
                );
            }
        }
    }

    private void assertPresaleCapacityCovered(
            List<Long> sourceItemIds,
            Map<Long, Long> inboundQuantityBySourceItemId
    ) {
        if (sourceItemIds.isEmpty()) {
            return;
        }
        for (SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary summary
                : salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
                        sourceItemIds,
                        null
                )) {
            long inboundQuantity = inboundQuantityBySourceItemId.getOrDefault(
                    summary.getSourcePurchaseOrderItemId(),
                    0L
            );
            long presaleQuantity = summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity();
            if (presaleQuantity > inboundQuantity) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源采购明细 " + summary.getSourcePurchaseOrderItemId()
                                + " 的预售数量超过最终入库量：已入库 " + inboundQuantity
                                + " 件，已占用 " + presaleQuantity + " 件，请先调整销售订单数量"
                );
            }
        }
    }

    private void lockSupplierLedger(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getSettlementCompanyId() == null || purchaseOrder.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能完成采购"
            );
        }
        supplierLedgerLockService.lock(
                purchaseOrder.getSettlementCompanyId(),
                purchaseOrder.getSupplierId()
        );
    }
}
