package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderCompletionSyncServiceTest {

    @Test
    void shouldMarkSalesOrderCompletedWhenAuditedOutboundExists() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository,
                salesOutboundRepository
        );

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-001");
        order.setStatus("已审核");

        SalesOutbound outbound = new SalesOutbound();
        outbound.setSalesOrderNo("SO-001");
        outbound.setStatus("已审核");

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(salesOutboundRepository.findByDeletedFlagFalse()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-001");

        assertThat(order.getStatus()).isEqualTo("完成销售");
        verify(salesOrderRepository).saveAll(List.of(order));
    }

    @Test
    void shouldRevertCompletedSalesOrderWhenNoAuditedOutboundRemains() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository,
                salesOutboundRepository
        );

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-002");
        order.setStatus("完成销售");

        SalesOutbound outbound = new SalesOutbound();
        outbound.setSalesOrderNo("SO-002");
        outbound.setStatus("草稿");

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(salesOutboundRepository.findByDeletedFlagFalse()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-002");

        assertThat(order.getStatus()).isEqualTo("已审核");
        verify(salesOrderRepository).saveAll(List.of(order));
    }

    @Test
    void shouldSkipSaveWhenStatusDoesNotNeedChange() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository,
                salesOutboundRepository
        );

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-003");
        order.setStatus("已审核");

        SalesOutbound outbound = new SalesOutbound();
        outbound.setSalesOrderNo("SO-999");
        outbound.setStatus("已审核");

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(salesOutboundRepository.findByDeletedFlagFalse()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-003");

        assertThat(order.getStatus()).isEqualTo("已审核");
        verify(salesOrderRepository, never()).saveAll(any());
    }

    @Test
    void shouldSupportCommaSeparatedSalesOrderReferences() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService service = new SalesOrderCompletionSyncService(
                salesOrderRepository,
                salesOutboundRepository
        );

        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-004");
        order.setStatus("已审核");

        SalesOutbound outbound = new SalesOutbound();
        outbound.setSalesOrderNo("SO-004, SO-005");
        outbound.setStatus("已审核");

        when(salesOrderRepository.findByOrderNoInAndDeletedFlagFalse(any())).thenReturn(List.of(order));
        when(salesOutboundRepository.findByDeletedFlagFalse()).thenReturn(List.of(outbound));

        service.syncBySalesOrderReference("SO-004");

        assertThat(order.getStatus()).isEqualTo("完成销售");
        verify(salesOrderRepository).saveAll(List.of(order));
    }
}
