package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundSaveServiceTest {

    @Test
    void shouldSyncCompletionByDistinctSourceItemIdsInsteadOfOrderNumberSnapshot() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        RecordingCompletionSyncService completionSyncService = new RecordingCompletionSyncService();
        SalesOutboundSaveService service = new SalesOutboundSaveService(repository, completionSyncService);
        SalesOutbound outbound = outbound("DISPLAY-ONLY-SNAPSHOT");
        outbound.setItems(List.of(
                outboundItem(outbound, 101L),
                outboundItem(outbound, null),
                outboundItem(outbound, 101L),
                outboundItem(outbound, 102L)
        ));

        when(repository.save(outbound)).thenReturn(outbound);

        service.save(outbound);

        assertThat(completionSyncService.sourceItemIds).containsExactly(101L, 102L);
        assertThat(outbound.getSalesOrderNo()).isEqualTo("DISPLAY-ONLY-SNAPSHOT");
    }

    @Test
    void shouldSaveOutboundAndSyncSalesOrderCompletion() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOutboundSaveService service = new SalesOutboundSaveService(repository, completionSyncService);
        SalesOutbound outbound = outbound("SO-001");
        outbound.setItems(List.of(outboundItem(outbound, 100L)));

        when(repository.save(outbound)).thenReturn(outbound);

        SalesOutbound saved = service.save(outbound);

        assertThat(saved).isSameAs(outbound);
        verify(repository).save(outbound);
        verify(completionSyncService).syncBySourceSalesOrderItemIds(List.of(100L));
    }

    @Test
    void shouldAllowMissingCompletionSyncServiceInLegacyTests() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOutboundSaveService service = new SalesOutboundSaveService(repository, null);
        SalesOutbound outbound = outbound("SO-001");

        when(repository.save(outbound)).thenReturn(outbound);

        SalesOutbound saved = service.save(outbound);

        assertThat(saved).isSameAs(outbound);
        verify(repository).save(outbound);
    }

    private SalesOutbound outbound(String salesOrderNo) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO-SAVE-001");
        outbound.setSalesOrderNo(salesOrderNo);
        return outbound;
    }

    private SalesOutboundItem outboundItem(SalesOutbound outbound, Long sourceSalesOrderItemId) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSalesOutbound(outbound);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        return item;
    }

    private static final class RecordingCompletionSyncService extends SalesOrderCompletionSyncService {

        private List<Long> sourceItemIds = List.of();

        private RecordingCompletionSyncService() {
            super(mock(com.leo.erp.sales.order.repository.SalesOrderRepository.class),
                    mock(com.leo.erp.sales.order.service.SalesOrderOutboundQueryService.class));
        }

        public void syncBySourceSalesOrderItemIds(Collection<Long> sourceSalesOrderItemIds) {
            sourceItemIds = List.copyOf(sourceSalesOrderItemIds);
        }
    }
}
