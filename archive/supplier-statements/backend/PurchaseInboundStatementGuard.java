package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseInboundStatementGuard {

    private final SupplierStatementRepository supplierStatementRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PurchaseInboundStatementGuard(
            SupplierStatementRepository supplierStatementRepository,
            SourceAllocationLockService sourceAllocationLockService
    ) {
        this.supplierStatementRepository = supplierStatementRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    public void assertStatusTransitionAllowed(
            PurchaseInbound inbound,
            String currentStatus,
            String nextStatus
    ) {
        if (!StatusConstants.INBOUND_COMPLETED.equals(currentStatus)
                || !StatusConstants.DRAFT.equals(nextStatus)) {
            return;
        }
        assertMutable(inbound, "反审核");
    }

    public void assertMutable(PurchaseInbound inbound, String action) {
        if (inbound == null || inbound.getId() == null) {
            return;
        }
        List<Long> inboundIds = List.of(inbound.getId());
        sourceAllocationLockService.lockDocumentSources(inboundIds, List.of(), List.of(), List.of());
        if (!supplierStatementRepository
                .findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(inboundIds, null)
                .isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购入库已被供应商对账单引用，不能" + action + "，请先删除相关供应商对账单"
            );
        }
    }

    public void assertSourceLineMutationAllowed(
            PurchaseInbound inbound,
            Collection<PurchaseInboundItemRequest> requestedItems,
            String action
    ) {
        if (sourceLinesChanged(inbound, requestedItems)) {
            assertMutable(inbound, action);
        }
    }

    private boolean sourceLinesChanged(
            PurchaseInbound inbound,
            Collection<PurchaseInboundItemRequest> requestedItems
    ) {
        List<PurchaseInboundItem> currentItems = inbound == null || inbound.getItems() == null
                ? List.of()
                : inbound.getItems().stream()
                .sorted(Comparator.comparing(PurchaseInboundItem::getLineNo))
                .toList();
        List<PurchaseInboundItemRequest> nextItems = requestedItems == null
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

    private boolean sameSourceLine(PurchaseInboundItem current, PurchaseInboundItemRequest next) {
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
                && Objects.equals(current.getSourcePurchaseOrderItemId(), next.sourcePurchaseOrderItemId())
                && Objects.equals(current.getWarehouseId(), next.warehouseId())
                && sameText(current.getWarehouseName(), next.warehouseName())
                && sameText(current.getSettlementMode(), next.settlementMode())
                && sameText(current.getBatchNo(), next.batchNo())
                && Objects.equals(current.getQuantity(), next.quantity())
                && sameText(current.getQuantityUnit(), next.quantityUnit())
                && sameNumber(current.getPieceWeightTon(), next.pieceWeightTon())
                && Objects.equals(current.getPiecesPerBundle(), next.piecesPerBundle())
                && sameOptionalNumber(current.getWeightTon(), next.weightTon())
                && sameOptionalNumber(current.getWeighWeightTon(), next.weighWeightTon())
                && sameOptionalNumber(current.getWeightAdjustmentTon(), next.weightAdjustmentTon())
                && sameOptionalNumber(current.getWeightAdjustmentAmount(), next.weightAdjustmentAmount())
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
