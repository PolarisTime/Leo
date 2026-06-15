package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseInboundCompletionSyncServiceTest {

    @Test
    void shouldCompleteInboundWhenReceivedQuantityIsWithinTolerance() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        PurchaseInbound inbound = inbound("已审核", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of());

        assertThat(service.shouldCompleteInbound(inbound)).isTrue();
    }

    @Test
    void shouldNotCompleteInboundWhenReceivedQuantityExceedsTolerance() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        PurchaseInbound inbound = inbound("已审核", 12);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of());

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldCompleteSourcePurchaseOrderWhenAllInboundDocumentsAreCompletedAndFulfilled() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 10);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound inbound = inbound("完成入库", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findByPurchaseOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(List.of(inbound));

        service.completeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("完成采购");
    }

    @Test
    void shouldNotCompleteSourcePurchaseOrderWhenAnyInboundDocumentIsNotCompleted() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 10);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound current = inbound("完成入库", 10);
        PurchaseInbound draft = inbound("已审核", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findByPurchaseOrderNoAndDeletedFlagFalse("PO-001")).thenReturn(List.of(current, draft));

        service.completeSourcePurchaseOrders(current);

        assertThat(sourceOrder.getStatus()).isEqualTo("已审核");
    }

    private PurchaseInboundSourceValidator sourceValidator(PurchaseOrderItemQueryService itemQueryService,
                                                           PurchaseInboundItemRepository inboundItemRepository) {
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                any(),
                any()
        )).thenReturn(List.of());
        return new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
    }

    private PurchaseInbound inbound(String status, Integer quantity) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("PI-001");
        inbound.setPurchaseOrderNo("PO-001");
        inbound.setStatus(status);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setSourcePurchaseOrderItemId(201L);
        item.setQuantity(quantity);
        inbound.getItems().add(item);
        return inbound;
    }

    private PurchaseOrder sourcePurchaseOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(301L);
        order.setOrderNo("PO-001");
        order.setStatus("已审核");
        return order;
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(Long id, Integer quantity) {
        return sourcePurchaseOrderItem(sourcePurchaseOrder(), id, quantity);
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(PurchaseOrder sourceOrder, Long id, Integer quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(sourceOrder);
        item.setQuantity(quantity);
        item.setWeightTon(BigDecimal.ONE);
        item.setAmount(BigDecimal.ONE);
        return item;
    }
}
