package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.service.SalesOutboundPreOutboundWeightSyncService;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderAllocatedWeightSyncService {

    private final SalesOrderItemRepository itemRepository;
    private final SalesOrderRepository orderRepository;
    private final PurchaseOrderItemPieceWeightRepository pieceWeightRepository;
    private final SalesOutboundRepository salesOutboundRepository;
    private final CustomerStatementRepository customerStatementRepository;
    private final InvoiceIssueRepository invoiceIssueRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final SalesOutboundPreOutboundWeightSyncService outboundWeightSyncService;

    public SalesOrderAllocatedWeightSyncService(SalesOrderItemRepository itemRepository,
                                                SalesOrderRepository orderRepository,
                                                PurchaseOrderItemPieceWeightRepository pieceWeightRepository,
                                                SalesOutboundRepository salesOutboundRepository,
                                                CustomerStatementRepository customerStatementRepository,
                                                InvoiceIssueRepository invoiceIssueRepository,
                                                ReceiptAllocationRepository receiptAllocationRepository,
                                                SalesOutboundPreOutboundWeightSyncService outboundWeightSyncService) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.pieceWeightRepository = pieceWeightRepository;
        this.salesOutboundRepository = salesOutboundRepository;
        this.customerStatementRepository = customerStatementRepository;
        this.invoiceIssueRepository = invoiceIssueRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.outboundWeightSyncService = outboundWeightSyncService;
    }

    @Transactional(readOnly = true)
    public Set<Long> findLockedSalesOrderItemIdsByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds) {
        List<SalesOrderItem> items = loadItemsByPurchaseOrderItemIds(purchaseOrderItemIds);
        return findLockedSalesOrderItemIds(items);
    }

    @Transactional
    public void syncByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds,
                                           Collection<Long> knownLockedSalesOrderItemIds) {
        List<SalesOrderItem> items = loadItemsByPurchaseOrderItemIds(purchaseOrderItemIds);
        if (items.isEmpty()) {
            return;
        }
        Set<Long> lockedIds = new LinkedHashSet<>(knownLockedIds(knownLockedSalesOrderItemIds));
        lockedIds.addAll(findLockedSalesOrderItemIds(items));
        List<SalesOrderItem> unlockedItems = items.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> !lockedIds.contains(item.getId()))
                .toList();
        if (unlockedItems.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> weightByItemId = loadAllocatedWeights(unlockedItems);
        if (weightByItemId.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> changedWeights = new LinkedHashMap<>();
        Set<SalesOrder> changedOrders = new LinkedHashSet<>();
        for (SalesOrderItem item : unlockedItems) {
            BigDecimal weightTon = weightByItemId.get(item.getId());
            if (weightTon == null) {
                continue;
            }
            item.setWeightTon(weightTon);
            item.setAmount(TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice()));
            changedWeights.put(item.getId(), weightTon);
            if (item.getSalesOrder() != null) {
                changedOrders.add(item.getSalesOrder());
            }
        }
        if (changedOrders.isEmpty()) {
            return;
        }
        changedOrders.forEach(this::refreshOrderTotals);
        orderRepository.saveAll(changedOrders.stream().toList());
        outboundWeightSyncService.syncBySalesOrderItemWeights(changedWeights);
    }

    private List<SalesOrderItem> loadItemsByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds) {
        List<Long> sourceIds = normalizeIds(purchaseOrderItemIds);
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return safeList(itemRepository.findActiveBySourcePurchaseOrderItemIds(sourceIds));
    }

    private Set<Long> findLockedSalesOrderItemIds(List<SalesOrderItem> items) {
        List<Long> itemIds = items.stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> lockedIds = items.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> item.getSalesOrder() != null)
                .filter(item -> StatusConstants.SALES_COMPLETED.equals(item.getSalesOrder().getStatus()))
                .map(SalesOrderItem::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        lockedIds.addAll(safeList(salesOutboundRepository.findSourceSalesOrderItemIdsByStatus(
                itemIds,
                StatusConstants.AUDITED
        )));
        lockedIds.addAll(safeList(customerStatementRepository.findSourceSalesOrderItemIds(itemIds)));
        lockedIds.addAll(safeList(invoiceIssueRepository.findSourceSalesOrderItemIdsByStatus(
                itemIds,
                StatusConstants.ISSUED
        )));
        lockedIds.addAll(safeList(receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                itemIds,
                StatusConstants.RECEIVED
        )));
        return lockedIds;
    }

    private Map<Long, BigDecimal> loadAllocatedWeights(List<SalesOrderItem> items) {
        List<Long> itemIds = items.stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return safeList(pieceWeightRepository.summarizeBySalesOrderItemIds(itemIds)).stream()
                .filter(summary -> summary.getSalesOrderItemId() != null)
                .collect(Collectors.toMap(
                        PurchaseOrderItemPieceWeightRepository.SalesOrderItemWeightSummary::getSalesOrderItemId,
                        summary -> TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon()),
                        (left, ignored) -> left
                ));
    }

    private void refreshOrderTotals(SalesOrder order) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : order.getItems()) {
            totalWeight = totalWeight.add(TradeItemCalculator.safeBigDecimal(item.getWeightTon()));
            totalAmount = totalAmount.add(TradeItemCalculator.safeBigDecimal(item.getAmount()));
        }
        order.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        order.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Set<Long> knownLockedIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
