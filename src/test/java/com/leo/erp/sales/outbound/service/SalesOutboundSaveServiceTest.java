package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOutboundSaveServiceTest {

    @Test
    void shouldSaveOutboundAndSyncSalesOrderCompletion() {
        SalesOutboundRepository repository = mock(SalesOutboundRepository.class);
        SalesOrderCompletionSyncService completionSyncService = mock(SalesOrderCompletionSyncService.class);
        SalesOutboundSaveService service = new SalesOutboundSaveService(repository, completionSyncService);
        SalesOutbound outbound = outbound("SO-001");

        when(repository.save(outbound)).thenReturn(outbound);

        SalesOutbound saved = service.save(outbound);

        assertThat(saved).isSameAs(outbound);
        verify(repository).save(outbound);
        verify(completionSyncService).syncBySalesOrderReference("SO-001");
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
}
