package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocation;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocationQuery;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseInboundCompletionSyncServiceTest {

    @Mock
    private PurchaseInboundRepository repository;

    @Mock
    private PurchaseInboundSourceValidator sourceValidator;

    @Mock
    private PurchaseInboundAllocationService allocationService;

    @Mock
    private PurchaseSupplierLedgerLock supplierLedgerLock;

    @Mock
    private PurchaseOrderSalesAllocationQuery purchaseOrderSalesAllocationQuery;

    @Mock
    private PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    private PurchaseInboundCompletionSyncService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseInboundCompletionSyncService(
                repository,
                sourceValidator,
                allocationService,
                supplierLedgerLock,
                purchaseOrderSalesAllocationQuery,
                purchaseOrderAuditPublisher
        );
    }

    private PurchaseInboundItem inboundItem(Long sourceItemId, Integer quantity) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setSourcePurchaseOrderItemId(sourceItemId);
        item.setQuantity(quantity);
        return item;
    }

    private PurchaseInbound inbound(Long id, String status, List<PurchaseInboundItem> items) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(id);
        inbound.setStatus(status);
        inbound.setItems(new ArrayList<>(items));
        return inbound;
    }

    private PurchaseOrderItem orderItem(Long id, Integer quantity, PurchaseOrder order) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setQuantity(quantity);
        item.setPurchaseOrder(order);
        return item;
    }

    private PurchaseOrder purchaseOrder(Long id, String status, Long companyId, Long supplierId,
                                        List<PurchaseOrderItem> items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setStatus(status);
        order.setSettlementCompanyId(companyId);
        order.setSupplierId(supplierId);
        order.setItems(new ArrayList<>(items));
        return order;
    }

    @Test
    void shouldCompleteInbound_shouldReturnFalseWhenStatusNotAudited() {
        assertThat(service.shouldCompleteInbound(
                inbound(1L, StatusConstants.DRAFT, List.of(inboundItem(1L, 10))))).isFalse();
    }

    @Test
    void shouldCompleteInbound_shouldReturnFalseWhenNoSourceItemIds() {
        assertThat(service.shouldCompleteInbound(
                inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(null, 10))))).isFalse();
    }

    @Test
    void shouldCompleteInbound_shouldReturnFalseWhenSourceMapEmpty() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of());

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldCompleteInbound_shouldReturnFalseWhenSourceItemMissing() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED,
                List.of(inboundItem(1L, 10), inboundItem(2L, 5)));
        PurchaseOrderItem sourceItem = orderItem(1L, 10, null);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L, 2L))).thenReturn(Map.of(1L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(1L, 2L), 1L)).thenReturn(Map.of(1L, 0));

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldCompleteInbound_shouldReturnTrueWhenFullyAllocated() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 6)));
        PurchaseOrderItem sourceItem = orderItem(1L, 10, null);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(1L), 1L)).thenReturn(Map.of(1L, 4));

        assertThat(service.shouldCompleteInbound(inbound)).isTrue();
    }

    @Test
    void shouldCompleteInbound_shouldReturnFalseWhenUnderAllocated() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 6)));
        PurchaseOrderItem sourceItem = orderItem(1L, 10, null);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(1L), 1L)).thenReturn(Map.of(1L, 3));

        assertThat(service.shouldCompleteInbound(inbound)).isFalse();
    }

    @Test
    void shouldCompleteInbound_shouldTreatNullQuantityAsZero() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, null)));
        PurchaseOrderItem sourceItem = orderItem(1L, 10, null);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(1L), 1L)).thenReturn(Map.of(1L, 10));

        assertThat(service.shouldCompleteInbound(inbound)).isTrue();
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldDoNothingWhenNoSourceIds() {
        PurchaseInbound inbound = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(null, 10)));

        service.synchronizeSourcePurchaseOrders(inbound, true);

        verify(sourceValidator, never()).loadSourcePurchaseOrderItemMap(any());
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldCompletePurchaseOrder() {
        PurchaseInbound trigger = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        PurchaseOrder order = purchaseOrder(500L, StatusConstants.AUDITED, 10L, 20L, new ArrayList<>());
        PurchaseOrderItem sourceItem = orderItem(1L, 10, order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(inbound(2L, StatusConstants.INBOUND_COMPLETED, List.of(inboundItem(1L, 10)))));
        when(purchaseOrderSalesAllocationQuery.summarizeByPurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of());

        service.synchronizeSourcePurchaseOrders(trigger, false);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
        verify(supplierLedgerLock).lock(10L, 20L);
        verify(purchaseOrderAuditPublisher).publish(order, "PURCHASE_ORDER_COMPLETED", "完成采购",
                "采购订单状态 已审核 -> 完成采购");
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldSkipNonAuditedOrder() {
        PurchaseInbound trigger = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        PurchaseOrder order = purchaseOrder(500L, StatusConstants.DRAFT, 10L, 20L, new ArrayList<>());
        PurchaseOrderItem sourceItem = orderItem(1L, 10, order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));

        service.synchronizeSourcePurchaseOrders(trigger, true);

        verify(repository, never()).findAllActiveBySourcePurchaseOrderItemIds(any());
        verify(purchaseOrderAuditPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldReopenCompletedOrderWhenNotFullyReceived() {
        PurchaseInbound trigger = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        PurchaseOrder order = purchaseOrder(500L, StatusConstants.PURCHASE_COMPLETED, 10L, 20L, new ArrayList<>());
        PurchaseOrderItem sourceItem = orderItem(1L, 10, order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(inbound(2L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)))));

        service.synchronizeSourcePurchaseOrders(trigger, true);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(purchaseOrderAuditPublisher).publish(order, "PURCHASE_ORDER_REOPENED", "退回已审核",
                "采购订单状态 完成采购 -> 已审核");
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldRejectLegacyDirectSalesExceedingInbound() {
        PurchaseInbound trigger = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        PurchaseOrder order = purchaseOrder(500L, StatusConstants.AUDITED, 10L, 20L, new ArrayList<>());
        PurchaseOrderItem sourceItem = orderItem(1L, 10, order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(inbound(2L, StatusConstants.INBOUND_COMPLETED, List.of(inboundItem(1L, 10)))));
        when(purchaseOrderSalesAllocationQuery.summarizeByPurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(new PurchaseOrderSalesAllocation(1L, 15L)));

        assertThatThrownBy(() -> service.synchronizeSourcePurchaseOrders(trigger, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史直连销售数量超过最终入库量");

        verify(purchaseOrderAuditPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void synchronizeSourcePurchaseOrders_shouldRejectWhenSupplierIdentityMissing() {
        PurchaseInbound trigger = inbound(1L, StatusConstants.AUDITED, List.of(inboundItem(1L, 10)));
        PurchaseOrder order = purchaseOrder(500L, StatusConstants.AUDITED, null, 20L, new ArrayList<>());
        PurchaseOrderItem sourceItem = orderItem(1L, 10, order);
        order.getItems().add(sourceItem);
        when(sourceValidator.loadSourcePurchaseOrderItemMap(List.of(1L))).thenReturn(Map.of(1L, sourceItem));
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(inbound(2L, StatusConstants.INBOUND_COMPLETED, List.of(inboundItem(1L, 10)))));
        when(purchaseOrderSalesAllocationQuery.summarizeByPurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.synchronizeSourcePurchaseOrders(trigger, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单缺少供应商或结算主体身份");
    }
}
