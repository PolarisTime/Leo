package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptSettlementSyncServiceTest {

    @Test
    void shouldCaptureOriginalStatementIds() {
        Receipt receipt = receipt(allocation(21L), allocation(22L), allocation(21L));
        ReceiptSettlementSyncService service = new ReceiptSettlementSyncService(mock(ApplicationEventPublisher.class));

        service.captureOriginalAllocationStatementIds(receipt);

        assertThat(receipt.getOriginalAllocationStatementIds()).containsExactly(21L, 22L);
    }

    @Test
    void shouldResolveLegacySourceStatementIdOnlyForSingleAllocation() {
        ReceiptSettlementSyncService service = new ReceiptSettlementSyncService(mock(ApplicationEventPublisher.class));

        assertThat(service.resolveLegacySourceStatementId(receipt(allocation(21L)))).isEqualTo(21L);
        assertThat(service.resolveLegacySourceStatementId(receipt(allocation(21L), allocation(22L)))).isNull();
    }

    @Test
    void shouldPublishEventsForOldAndCurrentStatementIds() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReceiptSettlementSyncService service = new ReceiptSettlementSyncService(eventPublisher);
        Receipt receipt = receipt(allocation(23L), allocation(null));
        receipt.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(21L, 22L)));

        service.syncCustomerStatements(receipt);

        ArgumentCaptor<ReceiptSettledEvent> captor = ArgumentCaptor.forClass(ReceiptSettledEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ReceiptSettledEvent::statementId)
                .containsExactly(21L, 22L, 23L);
    }

    private static Receipt receipt(ReceiptAllocation... allocations) {
        Receipt receipt = new Receipt();
        receipt.setItems(new ArrayList<>(List.of(allocations)));
        return receipt;
    }

    private static ReceiptAllocation allocation(Long statementId) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setSourceStatementId(statementId);
        return allocation;
    }
}
