package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderCompletionSyncService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderCompletionSyncServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderOutboundQueryService outboundQueryService;

    @Mock
    private BusinessOperationEventPublisher eventPublisher;

    @InjectMocks
    private SalesOrderCompletionSyncService service;

    private SalesOrderItem item(Long id, Integer quantity) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setQuantity(quantity);
        return item;
    }

    private SalesOrder order(Long id, String status, List<SalesOrderItem> items) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("SO" + id);
        order.setStatus(status);
        order.setItems(items);
        return order;
    }

    private SalesOrderOutboundQueryService.OutboundRecord outbound(String status,
                                                                   List<SalesOrderOutboundQueryService.OutboundItemRecord> items) {
        return new SalesOrderOutboundQueryService.OutboundRecord("SO001", status, items);
    }

    private SalesOrderOutboundQueryService.OutboundItemRecord obi(Long itemId, Integer qty) {
        return new SalesOrderOutboundQueryService.OutboundItemRecord(itemId, qty, null);
    }

    // ---------- syncBySourceSalesOrderItemIds ----------

    @Test
    void syncBySourceIds_shouldSkipNull() {
        service.syncBySourceSalesOrderItemIds(null);
        verifyNoInteractions(salesOrderRepository, outboundQueryService);
    }

    @Test
    void syncBySourceIds_shouldSkipEmpty() {
        service.syncBySourceSalesOrderItemIds(List.of());
        verifyNoInteractions(salesOrderRepository, outboundQueryService);
    }

    @Test
    void syncBySourceIds_shouldSkipAllNonPositiveIds() {
        service.syncBySourceSalesOrderItemIds(java.util.Arrays.asList(null, 0L, -5L));
        verifyNoInteractions(salesOrderRepository, outboundQueryService);
    }

    @Test
    void syncBySourceIds_shouldVerifyDeliveryWhenFullyOutbounded() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 10)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DELIVERY_VERIFICATION);
        verify(salesOrderRepository).saveAll(List.of(order));
        verify(eventPublisher).publish(eq("SALES_ORDER_DELIVERY_VERIFIED"), anyString(), anyString(),
                anyString(), anyString(), eq(1L), anyString(), anyString());
    }

    @Test
    void syncBySourceIds_shouldKeepAuditedWhenNotFullyOutbounded() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 5)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(salesOrderRepository, never()).saveAll(any());
    }

    // ---------- syncBySalesOrderId ----------

    @Test
    void syncById_shouldSkipNull() {
        service.syncBySalesOrderId(null);
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void syncById_shouldSkipNonPositive() {
        service.syncBySalesOrderId(0L);
        service.syncBySalesOrderId(-3L);
        verifyNoInteractions(salesOrderRepository);
    }

    @Test
    void syncById_shouldSyncWhenPresent() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, 10)));
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 10)))));

        service.syncBySalesOrderId(1L);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DELIVERY_VERIFICATION);
    }

    @Test
    void syncById_shouldSkipWhenAbsent() {
        when(salesOrderRepository.findByIdAndDeletedFlagFalse(1L))
                .thenReturn(java.util.Optional.empty());

        service.syncBySalesOrderId(1L);

        verify(salesOrderRepository, never()).saveAll(any());
    }

    // ---------- 状态机边界 ----------

    @Test
    void shouldNotChangeNonProtectedStatus() {
        SalesOrder order = order(1L, StatusConstants.DRAFT, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 10)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldReopenWhenDeliveryVerificationNotFullyOutbounded() {
        SalesOrder order = order(1L, StatusConstants.DELIVERY_VERIFICATION, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 5)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(salesOrderRepository).saveAll(List.of(order));
        verify(eventPublisher).publish(eq("SALES_ORDER_DELIVERY_REOPENED"), anyString(), anyString(),
                anyString(), anyString(), eq(1L), anyString(), anyString());
    }

    @Test
    void shouldKeepDeliveryVerificationWhenStillFullyOutbounded() {
        SalesOrder order = order(1L, StatusConstants.DELIVERY_VERIFICATION, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 10)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DELIVERY_VERIFICATION);
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldTreatNullItemQuantityAsZero() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, null)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 0)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED); // expected 0 → 不完全出库
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldIgnoreNonAuditedOutbounds() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.DRAFT, List.of(obi(11L, 10)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED); // 非已审核出库不计入
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldSkipWhenOrdersHaveNullItems() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.AUDITED);
        order.setItems(null);
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        verify(outboundQueryService, never()).findAuditedOutboundsBySourceSalesOrderItemIds(any());
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldSkipWhenAllItemIdsNull() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(null, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        verify(outboundQueryService, never()).findAuditedOutboundsBySourceSalesOrderItemIds(any());
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldSkipWhenRepositoryReturnsEmptyOrders() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of());

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        verifyNoInteractions(outboundQueryService, eventPublisher);
    }

    @Test
    void shouldSkipWhenRepositoryReturnsNullOrders() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(null);

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        verifyNoInteractions(outboundQueryService, eventPublisher);
    }

    @Test
    void shouldTreatNullOutboundQuantityAsZero() {
        SalesOrder order = order(1L, StatusConstants.AUDITED, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, null)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED); // 出库数量 null → 0 → 不完全出库
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldHandleNullOrderStatus() {
        SalesOrder order = order(1L, null, List.of(item(11L, 10)));
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of(order));
        when(outboundQueryService.findAuditedOutboundsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED, List.of(obi(11L, 10)))));

        service.syncBySourceSalesOrderItemIds(List.of(11L));

        assertThat(order.getStatus()).isNull(); // 非保护状态 → 不变
        verify(salesOrderRepository, never()).saveAll(any());
    }
}
