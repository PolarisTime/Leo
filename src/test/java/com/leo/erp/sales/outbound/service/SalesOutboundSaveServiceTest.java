package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundSaveService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundSaveServiceTest {

    @Mock
    private SalesOutboundRepository repository;

    @Mock
    private SalesOrderCompletionSyncService completionSyncService;

    @InjectMocks
    private SalesOutboundSaveService service;

    @Test
    void save_shouldSyncDistinctSourceItemIds() {
        SalesOutbound outbound = new SalesOutbound();
        SalesOutboundItem a = new SalesOutboundItem();
        a.setSourceSalesOrderItemId(11L);
        SalesOutboundItem b = new SalesOutboundItem();
        b.setSourceSalesOrderItemId(11L); // 重复
        SalesOutboundItem c = new SalesOutboundItem();
        c.setSourceSalesOrderItemId(null);
        outbound.setItems(List.of(a, b, c));
        when(repository.save(outbound)).thenReturn(outbound);

        assertThat(service.save(outbound)).isSameAs(outbound);
        verify(completionSyncService).syncBySourceSalesOrderItemIds(List.of(11L));
    }

    @Test
    void save_shouldNotSyncWhenItemsNull() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setItems(null); // 显式 null（默认是空 ArrayList）
        when(repository.save(outbound)).thenReturn(outbound);

        service.save(outbound);

        verifyNoInteractions(completionSyncService);
    }

    @Test
    void save_shouldNotSyncWhenNoSourceIds() {
        SalesOutbound outbound = new SalesOutbound();
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(null);
        outbound.setItems(List.of(item));
        when(repository.save(outbound)).thenReturn(outbound);

        service.save(outbound);

        verifyNoInteractions(completionSyncService);
    }

    @Test
    void save_shouldNotSyncWhenCompletionSyncServiceNull() {
        SalesOutboundSaveService svc = new SalesOutboundSaveService(repository, null);
        SalesOutbound outbound = new SalesOutbound();
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(11L);
        outbound.setItems(List.of(item));
        when(repository.save(outbound)).thenReturn(outbound);

        svc.save(outbound);

        verifyNoInteractions(completionSyncService);
    }
}
