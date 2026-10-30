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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        detachDeletedInboundResidue(order, requestedItems);
    }

    /**
     * 已软删除采购入库单的残留明细仍通过 RESTRICT 外键锁定来源订单明细：订单行被移除时，
     * 数据库会拒绝该物理删除。已删除的单据在业务上不应再阻止订单修改，因此在应用层先解除
     * 这些残留引用——**只针对本次真正被移除的那几行**，仍保留的订单行来源链路不动。
     *
     * <p>活跃入库单的引用不在此处处理：{@link #assertMutable} 已先拦截，
     * RESTRICT 外键仍作为最后一道防线保留。</p>
     */
    private void detachDeletedInboundResidue(
            PurchaseOrder order,
            Collection<PurchaseOrderItemRequest> requestedItems
    ) {
        List<Long> removedItemIds = removedSourceItemIds(order, requestedItems);
        if (removedItemIds.isEmpty()) {
            return;
        }
        purchaseInboundItemRepository.detachSourceReferencesOfDeletedInbounds(removedItemIds);
    }

    /** 本次保存中被移除的订单明细 ID：库里存在、请求里不再出现（新增行 id 为空，不参与比较）。 */
    private List<Long> removedSourceItemIds(
            PurchaseOrder order,
            Collection<PurchaseOrderItemRequest> requestedItems
    ) {
        Set<Long> requestedIds = new HashSet<>();
        if (requestedItems != null) {
            for (PurchaseOrderItemRequest item : requestedItems) {
                if (item != null && item.id() != null) {
                    requestedIds.add(item.id());
                }
            }
        }
        return SourceLineDiffSupport.sourceItemIds(
                        order == null ? null : order.getItems(), PurchaseOrderItem::getId)
                .stream()
                .filter(id -> !requestedIds.contains(id))
                .toList();
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
