package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderSaveServiceTest {

    @Test
    void shouldSyncAuditedOrderCompletionByParentOrderId() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        RecordingCompletionSyncService completionSyncService = new RecordingCompletionSyncService();
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.AUDITED);

        when(purchaseAllocationService.hasPurchaseOrderBackedItems(order)).thenReturn(false);
        when(repository.save(order)).thenReturn(order);

        service.save(order);

        assertThat(completionSyncService.salesOrderId).isEqualTo(1L);
        assertThat(order.getOrderNo()).isEqualTo("SO-SAVE-001");
    }

    @Test
    void shouldSaveDirectlyWhenOrderHasNoPurchaseOrderBackedItems() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.AUDITED);

        when(purchaseAllocationService.hasPurchaseOrderBackedItems(order)).thenReturn(false);
        when(repository.save(order)).thenReturn(order);

        SalesOrder saved = service.save(order);

        assertThat(saved).isSameAs(order);
        verify(repository).save(order);
        verify(repository, never()).saveAndFlush(order);
        verify(purchaseAllocationService, never()).finalizePurchaseOrderAllocations(order);
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void shouldFlushBeforeFinalizingPurchaseOrderAllocations() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.DRAFT);

        when(purchaseAllocationService.hasPurchaseOrderBackedItems(order)).thenReturn(true);
        when(repository.saveAndFlush(order)).thenReturn(order);
        when(repository.save(order)).thenReturn(order);

        SalesOrder saved = service.save(order);

        assertThat(saved).isSameAs(order);
        InOrder saveFlow = inOrder(repository, purchaseAllocationService);
        saveFlow.verify(repository).saveAndFlush(order);
        saveFlow.verify(purchaseAllocationService).finalizePurchaseOrderAllocations(order);
        saveFlow.verify(repository).save(order);
        verify(completionSyncService, never()).syncBySalesOrderId(1L);
    }

    @Test
    void shouldSaveAuditedPricingUpdateWithoutReallocatingPurchasePieces() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.AUDITED);

        when(repository.save(order)).thenReturn(order);

        SalesOrder saved = service.saveAuditedPricingUpdate(order);

        assertThat(saved).isSameAs(order);
        verify(repository).save(order);
        verify(repository, never()).saveAndFlush(order);
        verify(purchaseAllocationService, never()).hasPurchaseOrderBackedItems(order);
        verify(purchaseAllocationService, never()).finalizePurchaseOrderAllocations(order);
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void shouldSyncCompletionWhenResavingAuditedStatus() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.AUDITED);

        when(repository.save(order)).thenReturn(order);

        SalesOrder saved = service.saveStatus(order);

        assertThat(saved).isSameAs(order);
        verify(repository).save(order);
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void shouldNotSyncCompletionWhenSavingDraftStatus() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderPurchaseAllocationService purchaseAllocationService = mock(SalesOrderPurchaseAllocationService.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOrderSaveService service = new SalesOrderSaveService(
                repository,
                purchaseAllocationService,
                completionSyncService,
                new SalesOrderCompletionPolicy()
        );
        SalesOrder order = order(StatusConstants.DRAFT);

        when(repository.save(order)).thenReturn(order);

        SalesOrder saved = service.saveStatus(order);

        assertThat(saved).isSameAs(order);
        verify(repository).save(order);
        verify(completionSyncService, never()).syncBySalesOrderId(1L);
    }

    private SalesOrder order(String status) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-SAVE-001");
        order.setStatus(status);
        return order;
    }

    private static final class RecordingCompletionSyncService extends SalesOrderCompletionSyncService {

        private Long salesOrderId;

        private RecordingCompletionSyncService() {
            super(mock(SalesOrderRepository.class), mock(SalesOrderOutboundQueryService.class));
        }

        public void syncBySalesOrderId(Long id) {
            salesOrderId = id;
        }
    }
}
