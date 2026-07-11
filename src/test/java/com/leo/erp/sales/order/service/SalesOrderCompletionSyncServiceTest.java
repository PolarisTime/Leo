package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderCompletionSyncServiceTest {

    @Test
    void shouldMarkSalesOrderForDeliveryVerificationWhenAuditedOutboundExists() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-001", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-001", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-001");

        assertThat(order.getStatus()).isEqualTo("交付核定");
        verify(salesOrderRepository).saveAll(any());
    }

    @Test
    void shouldKeepAuditedWhenFullyOutboundedButUnitPriceIsZero() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-PRICE-001", "已审核", 10);
        order.getItems().get(0).setUnitPrice(BigDecimal.ZERO);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-PRICE-001", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-PRICE-001");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldNotOverwriteOrderWeightAndAmountFromAuditedOutbound() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-TOTAL-001", "已审核", 2);
        order.setTotalWeight(new BigDecimal("5.000"));
        order.setTotalAmount(new BigDecimal("15000.00"));
        SalesOrderItem orderItem = order.getItems().get(0);
        orderItem.setWeightTon(new BigDecimal("5.000"));
        orderItem.setUnitPrice(new BigDecimal("3000.00"));
        orderItem.setAmount(new BigDecimal("15000.00"));

        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-TOTAL-001", "已审核", orderItem.getId(), 2, new BigDecimal("4.500"));

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-TOTAL-001");

        assertThat(orderItem.getOriginalWeightTon()).isNull();
        assertThat(orderItem.getWeightTon()).isEqualByComparingTo("5.000");
        assertThat(orderItem.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("5.000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("15000.00");
        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldPreserveExistingOriginalWeightWhenOutboundWeightDiffers() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);
        SalesOrder order = buildOrder("SO-ORIGINAL-001", "已审核", 2);
        SalesOrderItem orderItem = order.getItems().get(0);
        orderItem.setOriginalWeightTon(new BigDecimal("6.000"));
        orderItem.setWeightTon(new BigDecimal("5.000"));
        orderItem.setUnitPrice(new BigDecimal("3000.00"));
        orderItem.setAmount(new BigDecimal("15000.00"));
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-ORIGINAL-001", "已审核", orderItem.getId(), 2, new BigDecimal("4.500"));

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-ORIGINAL-001");

        assertThat(orderItem.getOriginalWeightTon()).isEqualByComparingTo("6.000");
        assertThat(orderItem.getWeightTon()).isEqualByComparingTo("5.000");
        assertThat(orderItem.getAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void shouldNotStoreOriginalWeightWhenOutboundWeightMatchesCurrentWeight() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);
        SalesOrder order = buildOrder("SO-SAME-WEIGHT-001", "已审核", 2);
        SalesOrderItem orderItem = order.getItems().get(0);
        orderItem.setWeightTon(new BigDecimal("4.500"));
        orderItem.setUnitPrice(new BigDecimal("3000.00"));
        orderItem.setAmount(new BigDecimal("13500.00"));
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-SAME-WEIGHT-001", "已审核", orderItem.getId(), 2, new BigDecimal("4.500"));

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-SAME-WEIGHT-001");

        assertThat(orderItem.getOriginalWeightTon()).isNull();
        assertThat(orderItem.getWeightTon()).isEqualByComparingTo("4.500");
        assertThat(orderItem.getAmount()).isEqualByComparingTo("13500.00");
    }

    @Test
    void shouldNotSyncWeightWhenOrderItemHasNoUnitPrice() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);
        SalesOrder order = buildOrder("SO-NO-PRICE-001", "已审核", 2);
        order.setTotalAmount(new BigDecimal("15000.00"));
        SalesOrderItem orderItem = order.getItems().get(0);
        orderItem.setWeightTon(new BigDecimal("5.000"));
        orderItem.setUnitPrice(null);
        orderItem.setAmount(new BigDecimal("15000.00"));
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-NO-PRICE-001", "已审核", orderItem.getId(), 2, new BigDecimal("4.500"));

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NO-PRICE-001");

        assertThat(orderItem.getOriginalWeightTon()).isNull();
        assertThat(orderItem.getWeightTon()).isEqualByComparingTo("5.000");
        assertThat(orderItem.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void shouldRevertCompletedSalesOrderWhenNoAuditedOutboundRemains() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-002", "完成销售", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-002", "草稿", order.getItems().get(0).getId(), 5);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-002");

        assertThat(order.getStatus()).isEqualTo("已审核");
        verify(salesOrderRepository).saveAll(any());
    }

    @Test
    void shouldSkipSaveWhenCompletedStatusDoesNotNeedChange() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-003", "完成销售", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-003", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-003");

        assertThat(order.getStatus()).isEqualTo("完成销售");
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldKeepCompletedSalesOrderWhenAuditedOutboundStillExists() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-003B", "完成销售", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-003B", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-003B");

        assertThat(order.getStatus()).isEqualTo("完成销售");
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldSupportCommaSeparatedSalesOrderReferences() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-004", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-004, SO-005", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-004");

        assertThat(order.getStatus()).isEqualTo("交付核定");
        verify(salesOrderRepository).saveAll(any());
    }

    @Test
    void shouldIgnoreBlankSegmentsInCommaSeparatedSalesOrderReferences() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-004B", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-004B,  ,", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-004B");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldAcceptFulfillmentWithinTolerance() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-TOL-001", "已审核", 100);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-TOL-001", "已审核", order.getItems().get(0).getId(), 100);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-TOL-001");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldAcceptUnderFulfillmentWithinTolerance() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-TOL-002", "已审核", 100);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-TOL-002", "已审核", order.getItems().get(0).getId(), 96);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-TOL-002");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldRejectOverFulfillmentBeyondTolerance() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-TOL-003", "已审核", 100);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-TOL-003", "已审核", order.getItems().get(0).getId(), 106);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-TOL-003");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldRejectUnderFulfillmentBeyondTolerance() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-TOL-004", "已审核", 100);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-TOL-004", "已审核", order.getItems().get(0).getId(), 94);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-TOL-004");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldHandleZeroQuantityItemExactly() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-ZERO-001", "已审核", 0);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-ZERO-001", "已审核", order.getItems().get(0).getId(), 0);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-ZERO-001");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldNotCompleteWhenZeroQuantityItemHasOutbound() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-ZERO-002", "已审核", 0);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-ZERO-002", "已审核", order.getItems().get(0).getId(), 1);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-ZERO-002");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldReturnEarlyWhenReferenceIsBlank() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        service.syncBySalesOrderReference("");
        service.syncBySalesOrderReference(null);
        service.syncBySalesOrderReference("   ");

        verify(salesOrderRepository, never()).findByOrderNoInAndDeletedFlagFalse(any());
    }

    @Test
    void shouldReturnEarlyWhenNoOrdersFound() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of());

        service.syncBySalesOrderReference("SO-NONEXIST");

        verify(outboundQueryService, never()).findActiveOutbounds();
    }

    @Test
    void shouldNotChangeNonAuditedStatus() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-DRAFT-001", "草稿", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-DRAFT-001", "已审核", order.getItems().get(0).getId(), 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-DRAFT-001");

        assertThat(order.getStatus()).isEqualTo("草稿");
    }

    @Test
    void shouldHandleMultipleOrders() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order1 = buildOrder("SO-M-001", "已审核", 10);
        SalesOrder order2 = buildOrder("SO-M-002", "已审核", 5);
        SalesOrderOutboundQueryService.OutboundRecord outbound1 =
                buildOutbound("SO-M-001", "已审核", order1.getItems().get(0).getId(), 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound2 =
                buildOutbound("SO-M-002", "已审核", order2.getItems().get(0).getId(), 5);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order1, order2));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound1, outbound2));

        service.syncBySalesOrderReference("SO-M-001, SO-M-002");

        assertThat(order1.getStatus()).isEqualTo("交付核定");
        assertThat(order2.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldNotSaveWhenNoStatusChanged() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-NC-001", "草稿", 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of());

        service.syncBySalesOrderReference("SO-NC-001");

        assertThat(order.getStatus()).isEqualTo("草稿");
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldHandleMultipleItemsInOrder() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-MULTI-001");
        order.setStatus("已审核");
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setId(200L);
        item1.setSalesOrder(order);
        item1.setLineNo(1);
        item1.setMaterialCode("M1");
        item1.setQuantity(10);
        item1.setUnitPrice(BigDecimal.ONE);
        SalesOrderItem item2 = new SalesOrderItem();
        item2.setId(201L);
        item2.setSalesOrder(order);
        item2.setLineNo(2);
        item2.setMaterialCode("M2");
        item2.setQuantity(5);
        item2.setUnitPrice(BigDecimal.ONE);
        order.setItems(new ArrayList<>(List.of(item1, item2)));

        SalesOrderOutboundQueryService.OutboundRecord outbound = outbound(
                "SO-MULTI-001",
                "已审核",
                outboundItem(200L, 10),
                outboundItem(201L, 5)
        );

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-MULTI-001");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldNotCompleteWhenOneItemNotFullyOutbounded() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-PARTIAL-001");
        order.setStatus("已审核");
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setId(300L);
        item1.setSalesOrder(order);
        item1.setLineNo(1);
        item1.setMaterialCode("M1");
        item1.setQuantity(10);
        item1.setUnitPrice(BigDecimal.ONE);
        SalesOrderItem item2 = new SalesOrderItem();
        item2.setId(301L);
        item2.setSalesOrder(order);
        item2.setLineNo(2);
        item2.setMaterialCode("M2");
        item2.setQuantity(5);
        item2.setUnitPrice(BigDecimal.ONE);
        order.setItems(new ArrayList<>(List.of(item1, item2)));

        SalesOrderOutboundQueryService.OutboundRecord outbound = outbound(
                "SO-PARTIAL-001",
                "已审核",
                outboundItem(300L, 10)
        );

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-PARTIAL-001");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldHandleOutboundWithNullQuantity() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-NULL-QTY", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-NULL-QTY", "已审核", order.getItems().get(0).getId(), null);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NULL-QTY");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldHandleOutboundWithNullSourceSalesOrderItemId() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-NULL-SRC", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound = outbound(
                "SO-NULL-SRC",
                "已审核",
                outboundItem(null, 10)
        );

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NULL-SRC");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldHandleOutboundWithNullOrderNo() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = buildOrder("SO-NULL-ONO", "已审核", 10);
        SalesOrderOutboundQueryService.OutboundRecord outbound = outbound(
                null,
                "已审核",
                outboundItem(order.getItems().get(0).getId(), 10)
        );

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NULL-ONO");

        assertThat(order.getStatus()).isEqualTo("已审核");
    }

    @Test
    void shouldHandleOrderWithNullQuantityItem() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-NULL-ITEM-QTY");
        order.setStatus("已审核");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(400L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setQuantity(null);
        item.setUnitPrice(BigDecimal.ONE);
        order.setItems(new ArrayList<>(List.of(item)));

        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-NULL-ITEM-QTY", "已审核", 400L, 0);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NULL-ITEM-QTY");

        assertThat(order.getStatus()).isEqualTo("交付核定");
    }

    @Test
    void shouldHandleOrderWithNullStatus() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderOutboundQueryService outboundQueryService = mock(SalesOrderOutboundQueryService.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository, outboundQueryService);

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-NULL-STATUS");
        order.setStatus(null);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(500L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setQuantity(10);
        item.setUnitPrice(BigDecimal.ONE);
        order.setItems(new ArrayList<>(List.of(item)));

        SalesOrderOutboundQueryService.OutboundRecord outbound =
                buildOutbound("SO-NULL-STATUS", "已审核", 500L, 10);

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(outboundQueryService.findActiveOutbounds()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-NULL-STATUS");

        assertThat(order.getStatus()).isNull();
        verify(salesOrderRepository, never()).saveAll(any());
    }

    private SalesOrder buildOrder(String orderNo, String status, int quantity) {
        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(100L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.ONE);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrderOutboundQueryService.OutboundRecord buildOutbound(String salesOrderNo,
                                                                        String status,
                                                                        Long sourceItemId,
                                                                        Integer quantity) {
        return buildOutbound(salesOrderNo, status, sourceItemId, quantity, null);
    }

    private SalesOrderOutboundQueryService.OutboundRecord buildOutbound(String salesOrderNo,
                                                                        String status,
                                                                        Long sourceItemId,
                                                                        Integer quantity,
                                                                        BigDecimal weightTon) {
        return outbound(salesOrderNo, status, new SalesOrderOutboundQueryService.OutboundItemRecord(
                sourceItemId,
                quantity,
                weightTon
        ));
    }

    private SalesOrderOutboundQueryService.OutboundRecord outbound(
            String salesOrderNo,
            String status,
            SalesOrderOutboundQueryService.OutboundItemRecord... items
    ) {
        return new SalesOrderOutboundQueryService.OutboundRecord(salesOrderNo, status, List.of(items));
    }

    private SalesOrderOutboundQueryService.OutboundItemRecord outboundItem(Long sourceItemId, Integer quantity) {
        return new SalesOrderOutboundQueryService.OutboundItemRecord(sourceItemId, quantity, null);
    }
}
