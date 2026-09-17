package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SourceLineDiffSupport;
import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.leo.erp.common.support.SourceLineDiffSupport.sameNumber;
import static com.leo.erp.common.support.SourceLineDiffSupport.sameOptionalNumber;
import static com.leo.erp.common.support.SourceLineDiffSupport.sameText;

@Service
public class PurchaseOrderDownstreamMutationGuard {

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PurchaseOrderDownstreamMutationGuard(
            PurchaseInboundItemRepository purchaseInboundItemRepository,
            PurchaseOrderReferenceGuard purchaseOrderReferenceGuard,
            SourceAllocationLockService sourceAllocationLockService
    ) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderReferenceGuard = purchaseOrderReferenceGuard;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertMutable(PurchaseOrder order, String action) {
        List<Long> itemIds = SourceLineDiffSupport.sourceItemIds(
                order == null ? null : order.getItems(), PurchaseOrderItem::getId);
        if (itemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(itemIds, List.of(), List.of());
        if (!purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(itemIds).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单已存在采购入库单，不能" + action + "，请先删除相关采购入库单"
            );
        }
        if (purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(itemIds)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单已被销售订单引用，不能" + action + "，请先删除相关销售订单"
            );
        }
    }

    public void assertSourceLineMutationAllowed(
            PurchaseOrder order,
            Collection<PurchaseOrderItemRequest> requestedItems,
            String action
    ) {
        if (!sourceLinesChanged(order, requestedItems)) {
            return;
        }
        assertMutable(order, action);
        assertNoDeletedInboundResidue(order, action);
    }

    /**
     * 已软删除采购入库单的残留明细同样通过 RESTRICT 外键物理锁定来源采购订单明细，
     * 行数/行内容变更会因外键失败被数据库拒绝；此处提前给出明确业务提示，避免暴露底层完整性错误。
     */
    private void assertNoDeletedInboundResidue(PurchaseOrder order, String action) {
        List<Long> itemIds = SourceLineDiffSupport.sourceItemIds(
                order == null ? null : order.getItems(), PurchaseOrderItem::getId);
        if (itemIds.isEmpty()) {
            return;
        }
        if (!purchaseInboundItemRepository.findAllBySourcePurchaseOrderItemIds(itemIds).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单明细仍被已删除的采购入库单引用，不能" + action + "，请先清理采购入库单残留数据"
            );
        }
    }

    private boolean sourceLinesChanged(
            PurchaseOrder order,
            Collection<PurchaseOrderItemRequest> requestedItems
    ) {
        return SourceLineDiffSupport.sourceLinesChanged(
                order == null ? null : order.getItems(),
                requestedItems,
                PurchaseOrderItem::getLineNo,
                this::sameSourceLine
        );
    }

    private boolean sameSourceLine(PurchaseOrderItem current, PurchaseOrderItemRequest next) {
        return next != null
                && Objects.equals(current.getId(), next.id())
                && Objects.equals(current.getMaterialId(), next.materialId())
                && sameText(current.getMaterialCode(), next.materialCode())
                && sameText(current.getBrand(), next.brand())
                && sameText(current.getCategory(), next.category())
                && sameText(current.getMaterial(), next.material())
                && sameText(current.getSpec(), next.spec())
                && sameText(current.getLength(), next.length())
                && sameText(current.getUnit(), next.unit())
                && Objects.equals(current.getWarehouseId(), next.warehouseId())
                && sameText(current.getWarehouseName(), next.warehouseName())
                && sameText(current.getBatchNo(), next.batchNo())
                && Objects.equals(current.getQuantity(), next.quantity())
                && sameText(current.getQuantityUnit(), next.quantityUnit())
                && sameNumber(current.getPieceWeightTon(), next.pieceWeightTon())
                && Objects.equals(current.getPiecesPerBundle(), next.piecesPerBundle())
                && sameOptionalNumber(current.getWeightTon(), next.weightTon())
                && sameNumber(current.getUnitPrice(), next.unitPrice());
    }
}
