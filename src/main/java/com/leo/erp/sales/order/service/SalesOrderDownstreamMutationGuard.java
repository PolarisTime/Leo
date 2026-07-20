package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderDownstreamMutationGuard {

    private final SalesOutboundRepository salesOutboundRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final FreightBillSourceOrderRepository freightSourceRepository;

    public SalesOrderDownstreamMutationGuard(
            SalesOutboundRepository salesOutboundRepository,
            SourceAllocationLockService sourceAllocationLockService,
            FreightBillSourceOrderRepository freightSourceRepository
    ) {
        this.salesOutboundRepository = salesOutboundRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.freightSourceRepository = freightSourceRepository;
    }

    public void assertMutable(SalesOrder order, String action) {
        assertNoFreightReference(order, action);
        List<Long> itemIds = sourceItemIds(order);
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
        List<FreightBillSourceOrder> relations = freightSourceRepository.findActiveBySourceOrderId(order.getId());
        if (relations.isEmpty()) {
            return;
        }
        String billNo = relations.get(0).getFreightBill().getBillNo();
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "销售订单已关联物流单" + billNo + "，请先删除物流单后再" + action
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

    private List<Long> sourceItemIds(SalesOrder order) {
        if (order == null || order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean sourceLinesChanged(
            SalesOrder order,
            Collection<SalesOrderItemRequest> requestedItems
    ) {
        List<SalesOrderItem> currentItems = order == null || order.getItems() == null
                ? List.of()
                : order.getItems().stream()
                .sorted(Comparator.comparing(SalesOrderItem::getLineNo))
                .toList();
        List<SalesOrderItemRequest> nextItems = requestedItems == null
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
