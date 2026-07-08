package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderAllocatedWeightSyncServiceTest {

    @Test
    void shouldSyncUnlockedSalesOrderItemsAndCascadeToPreOutbound() {
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        PurchaseOrderItemPieceWeightRepository pieceWeightRepository = mock(PurchaseOrderItemPieceWeightRepository.class);
        SalesOutboundPreOutboundWeightSyncService outboundSyncService =
                mock(SalesOutboundPreOutboundWeightSyncService.class);
        SalesOrderAllocatedWeightSyncService service = new SalesOrderAllocatedWeightSyncService(
                itemRepository,
                orderRepository,
                pieceWeightRepository,
                mock(SalesOutboundRepository.class),
                mock(CustomerStatementRepository.class),
                mock(InvoiceIssueRepository.class),
                mock(ReceiptAllocationRepository.class),
                outboundSyncService
        );

        SalesOrder order = salesOrder(StatusConstants.AUDITED);
        SalesOrderItem item = salesOrderItem(order, 301L, 201L, "1.000", "4000.00");
        order.getItems().add(item);
        var summary = mock(PurchaseOrderItemPieceWeightRepository.SalesOrderItemWeightSummary.class);
        when(summary.getSalesOrderItemId()).thenReturn(301L);
        when(summary.getTotalWeightTon()).thenReturn(new BigDecimal("2.500"));
        when(itemRepository.findActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(item));
        when(pieceWeightRepository.summarizeBySalesOrderItemIds(List.of(301L))).thenReturn(List.of(summary));

        service.syncByPurchaseOrderItemIds(List.of(201L), Set.of());

        assertThat(item.getWeightTon()).isEqualByComparingTo("2.50000000");
        assertThat(item.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("2.50000000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("10000.00");
        verify(orderRepository).saveAll(List.of(order));
        verify(outboundSyncService).syncBySalesOrderItemWeights(Map.of(301L, new BigDecimal("2.50000000")));
    }

    @Test
    void shouldExposeLockedSalesOrderItemIdsByPurchaseOrderItems() {
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        PurchaseOrderItemPieceWeightRepository pieceWeightRepository = mock(PurchaseOrderItemPieceWeightRepository.class);
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        CustomerStatementRepository customerStatementRepository = mock(CustomerStatementRepository.class);
        InvoiceIssueRepository invoiceIssueRepository = mock(InvoiceIssueRepository.class);
        ReceiptAllocationRepository receiptAllocationRepository = mock(ReceiptAllocationRepository.class);
        SalesOrderAllocatedWeightSyncService service = new SalesOrderAllocatedWeightSyncService(
                itemRepository,
                orderRepository,
                pieceWeightRepository,
                outboundRepository,
                customerStatementRepository,
                invoiceIssueRepository,
                receiptAllocationRepository,
                mock(SalesOutboundPreOutboundWeightSyncService.class)
        );

        SalesOrder completedOrder = salesOrder(StatusConstants.SALES_COMPLETED);
        SalesOrderItem completedItem = salesOrderItem(completedOrder, 301L, 201L, "1.000", "4000.00");
        SalesOrderItem outboundLockedItem = salesOrderItem(salesOrder(StatusConstants.AUDITED), 302L, 201L, "1.000", "4000.00");
        SalesOrderItem statementLockedItem = salesOrderItem(salesOrder(StatusConstants.AUDITED), 303L, 201L, "1.000", "4000.00");
        SalesOrderItem invoiceLockedItem = salesOrderItem(salesOrder(StatusConstants.AUDITED), 304L, 201L, "1.000", "4000.00");
        SalesOrderItem receiptLockedItem = salesOrderItem(salesOrder(StatusConstants.AUDITED), 305L, 201L, "1.000", "4000.00");
        when(itemRepository.findActiveBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(completedItem, outboundLockedItem, statementLockedItem, invoiceLockedItem, receiptLockedItem));
        when(outboundRepository.findSourceSalesOrderItemIdsByStatus(List.of(301L, 302L, 303L, 304L, 305L), StatusConstants.AUDITED))
                .thenReturn(List.of(302L));
        when(customerStatementRepository.findSourceSalesOrderItemIds(List.of(301L, 302L, 303L, 304L, 305L)))
                .thenReturn(List.of(303L));
        when(invoiceIssueRepository.findSourceSalesOrderItemIdsByStatus(List.of(301L, 302L, 303L, 304L, 305L), StatusConstants.ISSUED))
                .thenReturn(List.of(304L));
        when(receiptAllocationRepository.findReceivedSourceSalesOrderItemIds(
                List.of(301L, 302L, 303L, 304L, 305L),
                StatusConstants.RECEIVED
        )).thenReturn(List.of(305L));

        Set<Long> lockedIds = service.findLockedSalesOrderItemIdsByPurchaseOrderItemIds(List.of(201L));

        assertThat(lockedIds).containsExactlyInAnyOrder(301L, 302L, 303L, 304L, 305L);
    }

    private SalesOrder salesOrder(String status) {
        SalesOrder order = new SalesOrder();
        order.setId((long) status.hashCode());
        order.setStatus(status);
        order.setTotalWeight(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        return order;
    }

    private SalesOrderItem salesOrderItem(SalesOrder order,
                                          Long id,
                                          Long sourcePurchaseOrderItemId,
                                          String weightTon,
                                          String unitPrice) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        item.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(BigDecimal.ZERO);
        return item;
    }
}
