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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundCompletionSyncServiceTest {

    @Test
    void shouldCompleteAuditedInboundAfterRefundConsumesRemainingQuantity() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundSourceValidator sourceValidator = mock(PurchaseInboundSourceValidator.class);
        PurchaseInboundAllocationService allocationService = mock(PurchaseInboundAllocationService.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator,
                allocationService
        );
        PurchaseInbound inbound = inbound("已审核", 4);
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(201L)))
                .thenReturn(Map.of(201L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(201L), 1L))
                .thenReturn(Map.of(201L, 6));

        service.synchronizeAfterPurchaseRefundStatusChange(List.of(201L));

        assertThat(inbound.getStatus()).isEqualTo("完成入库");
        verify(repository).saveAll(List.of(inbound));
    }

    @Test
    void shouldRestoreRefundCompletedInboundToAuditedAfterRefundIsUnaudited() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundSourceValidator sourceValidator = mock(PurchaseInboundSourceValidator.class);
        PurchaseInboundAllocationService allocationService = mock(PurchaseInboundAllocationService.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator,
                allocationService
        );
        PurchaseInbound inbound = inbound("完成入库", 4);
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(201L)))
                .thenReturn(Map.of(201L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(201L), 1L))
                .thenReturn(Map.of());

        service.synchronizeAfterPurchaseRefundStatusChange(List.of(201L));

        assertThat(inbound.getStatus()).isEqualTo("已审核");
        verify(repository).saveAll(List.of(inbound));
    }

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
    void shouldNotCompleteInboundWhenReceivedQuantityIsFivePercentShort() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 100);
        PurchaseInbound inbound = inbound("已审核", 95);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldNotCompleteInboundWhenInboundIsNotAudited() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        assertThat(service.shouldCompleteInbound(inbound("草稿", 10))).isFalse();
        verifyNoInteractions(itemQueryService);
    }

    @Test
    void shouldNotCompleteInboundWhenNoSourcePurchaseOrderItems() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        assertThat(service.shouldCompleteInbound(inboundWithoutSource("已审核", 10))).isFalse();
        verifyNoInteractions(itemQueryService);
    }

    @Test
    void shouldNotCompleteInboundWhenSourcePurchaseOrderItemMapIsEmpty() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of());

        assertThat(service.shouldCompleteInbound(inbound("已审核", 10))).isFalse();
    }

    @Test
    void shouldNotCompleteInboundWhenAnySourceItemIsMissing() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 10);
        PurchaseInbound inbound = inbound("已审核", 10);
        inbound.getItems().add(inboundItem(102L, 202L, 5));
        when(itemQueryService.findActiveByIdIn(List.of(201L, 202L))).thenReturn(List.of(sourceItem));

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldCompleteInboundWhenSourceQuantityAndCurrentQuantityAreNull() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, null);
        PurchaseInbound inbound = inbound("已审核", null);
        inbound.getItems().add(inboundItem(102L, null, 7));
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        assertThat(service.shouldCompleteInbound(inbound)).isTrue();
    }

    @Test
    void shouldNotCompleteInboundWhenExpectedQuantityIsZeroButActualQuantityIsNotZero() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(201L, 0);
        PurchaseInbound inbound = inbound("已审核", 1);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

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
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));

        service.synchronizeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("完成采购");
    }

    @Test
    void shouldReopenCompletedPurchaseOrderWhenInboundIsReversed() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        sourceOrder.setStatus("完成采购");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 10);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound reversedInbound = inbound("草稿", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(reversedInbound));

        service.synchronizeSourcePurchaseOrders(reversedInbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("已审核");
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
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(current, draft));

        service.synchronizeSourcePurchaseOrders(current);

        assertThat(sourceOrder.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldReturnWhenCompletingSourcePurchaseOrdersWithoutSourceItems() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        service.synchronizeSourcePurchaseOrders(inboundWithoutSource("完成入库", 10));

        verifyNoInteractions(itemQueryService, repository);
    }

    @Test
    void shouldIgnoreSourceItemsWithoutPurchaseOrderWhenCompletingSourcePurchaseOrders() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(null, 201L, 10);
        PurchaseInbound inbound = inbound("完成入库", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        service.synchronizeSourcePurchaseOrders(inbound);

        verifyNoInteractions(repository);
    }

    @Test
    void shouldNotCompleteSourcePurchaseOrderWhenPurchaseOrderIsNotAudited() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        sourceOrder.setStatus("草稿");
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 10);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound inbound = inbound("完成入库", 10);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));

        service.synchronizeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("草稿");
        verifyNoInteractions(repository);
    }

    @Test
    void shouldCompleteSourcePurchaseOrderWhenNullQuantitiesAreTreatedAsZero() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, null);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound inbound = inbound("完成入库", null);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));

        service.synchronizeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("完成采购");
    }

    @Test
    void shouldCompleteSourcePurchaseOrderIgnoringInboundItemsWithoutSource() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 10);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound inbound = inbound("完成入库", 10);
        inbound.getItems().add(inboundItem(102L, null, 99));
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));

        service.synchronizeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("完成采购");
    }

    @Test
    void shouldNotCompleteSourcePurchaseOrderWhenCompletedInboundsDoNotFulfillQuantity() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundCompletionSyncService service = completionSyncService(
                repository,
                itemQueryService,
                inboundItemRepository
        );

        PurchaseOrder sourceOrder = sourcePurchaseOrder();
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(sourceOrder, 201L, 100);
        sourceOrder.getItems().add(sourceItem);
        PurchaseInbound inbound = inbound("完成入库", 94);
        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(inbound));

        service.synchronizeSourcePurchaseOrders(inbound);

        assertThat(sourceOrder.getStatus()).isEqualTo("已审核");
    }

    private PurchaseInboundCompletionSyncService completionSyncService(PurchaseInboundRepository repository,
                                                                       PurchaseOrderItemQueryService itemQueryService,
                                                                       PurchaseInboundItemRepository inboundItemRepository) {
        return new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator(itemQueryService, inboundItemRepository),
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
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
        inbound.getItems().add(inboundItem(101L, 201L, quantity));
        return inbound;
    }

    private PurchaseInbound inboundWithoutSource(String status, Integer quantity) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("PI-001");
        inbound.setPurchaseOrderNo("PO-001");
        inbound.setStatus(status);
        inbound.getItems().add(inboundItem(101L, null, quantity));
        return inbound;
    }

    private PurchaseInboundItem inboundItem(Long id, Long sourcePurchaseOrderItemId, Integer quantity) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        item.setQuantity(quantity);
        return item;
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
