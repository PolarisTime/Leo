package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SourceLineDiffSupport;
import com.leo.erp.sales.api.SalesOrderDownstreamReference;
import com.leo.erp.sales.api.SalesOrderReferenceGuard;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.leo.erp.common.support.SourceLineDiffSupport.sameNumber;
import static com.leo.erp.common.support.SourceLineDiffSupport.sameOptionalNumber;
import static com.leo.erp.common.support.SourceLineDiffSupport.sameText;

@Service
public class SalesOrderDownstreamMutationGuard {

    private final SalesOutboundRepository salesOutboundRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOrderReferenceGuard salesOrderReferenceGuard;

    public SalesOrderDownstreamMutationGuard(
            SalesOutboundRepository salesOutboundRepository,
            SourceAllocationLockService sourceAllocationLockService,
            SalesOrderReferenceGuard salesOrderReferenceGuard
    ) {
        this.salesOutboundRepository = salesOutboundRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.salesOrderReferenceGuard = salesOrderReferenceGuard;
    }

    public void assertMutable(SalesOrder order, String action) {
        assertNoFreightReference(order, action);
        List<Long> itemIds = SourceLineDiffSupport.sourceItemIds(
                order == null ? null : order.getItems(), SalesOrderItem::getId);
        if (itemIds.isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(List.of(), List.of(), itemIds);
        if (!salesOutboundRepository
                .findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(itemIds, null)
                .isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "销售订单已存在销售出库单，不能" + action + "，请先删除相关销售出库单"
            );
        }
    }

    public void assertNoFreightReference(SalesOrder order, String action) {
        if (order == null || order.getId() == null) {
            return;
        }
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                List.of(order.getId()),
                List.of(),
                List.of()
        );
        SalesOrderDownstreamReference reference = salesOrderReferenceGuard
                .findActiveReference(order.getId())
                .orElse(null);
        if (reference == null) {
            return;
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "销售订单已关联物流单" + reference.documentNo() + "，请先删除物流单后再" + action
        );
    }

    public void assertSourceLineMutationAllowed(
            SalesOrder order,
            Collection<SalesOrderItemRequest> requestedItems,
            String action
    ) {
        if (sourceLinesChanged(order, requestedItems)) {
            assertMutable(order, action);
        }
    }

    private boolean sourceLinesChanged(
            SalesOrder order,
            Collection<SalesOrderItemRequest> requestedItems
    ) {
        return SourceLineDiffSupport.sourceLinesChanged(
                order == null ? null : order.getItems(),
                requestedItems,
                SalesOrderItem::getLineNo,
                this::sameSourceLine
        );
    }

    private boolean sameSourceLine(SalesOrderItem current, SalesOrderItemRequest next) {
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
                && Objects.equals(current.getSourceInboundItemId(), next.sourceInboundItemId())
                && Objects.equals(current.getSourcePurchaseOrderItemId(), next.sourcePurchaseOrderItemId())
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
