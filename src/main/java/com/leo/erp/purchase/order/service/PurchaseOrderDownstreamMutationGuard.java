package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseOrderDownstreamMutationGuard {

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PurchaseOrderDownstreamMutationGuard(
            PurchaseInboundItemRepository purchaseInboundItemRepository,
            SalesOrderItemRepository salesOrderItemRepository,
            SourceAllocationLockService sourceAllocationLockService
    ) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertMutable(PurchaseOrder order, String action) {
        List<Long> itemIds = sourceItemIds(order);
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
        if (!salesOrderItemRepository.findActiveBySourcePurchaseOrderItemIds(itemIds).isEmpty()) {
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
        if (sourceLinesChanged(order, requestedItems)) {
            assertMutable(order, action);
        }
    }

    private List<Long> sourceItemIds(PurchaseOrder order) {
        if (order == null || order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean sourceLinesChanged(
            PurchaseOrder order,
            Collection<PurchaseOrderItemRequest> requestedItems
    ) {
        List<PurchaseOrderItem> currentItems = order == null || order.getItems() == null
                ? List.of()
                : order.getItems().stream()
                .sorted(Comparator.comparing(PurchaseOrderItem::getLineNo))
                .toList();
        List<PurchaseOrderItemRequest> nextItems = requestedItems == null
                ? List.of()
                : List.copyOf(requestedItems);
        if (currentItems.size() != nextItems.size()) {
            return true;
        }
        for (int index = 0; index < currentItems.size(); index++) {
            if (!sameSourceLine(currentItems.get(index), nextItems.get(index))) {
                return true;
            }
        }
        return false;
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

    private boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean sameOptionalNumber(BigDecimal current, BigDecimal requested) {
        return requested == null || sameNumber(current, requested);
    }
}
