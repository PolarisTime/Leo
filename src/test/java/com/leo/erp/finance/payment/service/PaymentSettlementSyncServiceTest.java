package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentSettlementSyncServiceTest {

    @Test
    void shouldCaptureOriginalBusinessTypeAndStatementIds() {
        Payment payment = payment("供应商", allocation(11L), allocation(12L), allocation(11L));
        PaymentSettlementSyncService service = new PaymentSettlementSyncService(mock(ApplicationEventPublisher.class));

        service.captureOriginalAllocationState(payment);

        assertThat(payment.getOriginalBusinessType()).isEqualTo("供应商");
        assertThat(payment.getOriginalAllocationStatementIds()).containsExactly(11L, 12L);
    }

    @Test
    void shouldResolveLegacySourceStatementIdOnlyForSingleAllocation() {
        PaymentSettlementSyncService service = new PaymentSettlementSyncService(mock(ApplicationEventPublisher.class));

        assertThat(service.resolveLegacySourceStatementId(payment("供应商", allocation(11L)))).isEqualTo(11L);
        assertThat(service.resolveLegacySourceStatementId(payment("供应商", allocation(11L), allocation(12L)))).isNull();
    }

    @Test
    void shouldPublishEventsForOldAndCurrentStatementLinks() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PaymentSettlementSyncService service = new PaymentSettlementSyncService(eventPublisher);
        Payment payment = payment("物流商", allocation(21L), allocation(null));
        payment.setOriginalBusinessType("供应商");
        payment.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(11L, 12L)));

        service.syncLinkedStatements(payment);

        ArgumentCaptor<PaymentSettledEvent> captor = ArgumentCaptor.forClass(PaymentSettledEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(event -> event.statementId() + ":" + event.businessType())
                .containsExactly("11:供应商", "12:供应商", "21:物流商");
    }

    @Test
    void shouldSkipInvalidStatementLinks() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PaymentSettlementSyncService service = new PaymentSettlementSyncService(eventPublisher);
        Payment payment = payment(null, allocation(21L));

        service.captureOriginalAllocationState(payment("供应商", allocation(null)));
        service.syncLinkedStatements(payment);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private static Payment payment(String businessType, PaymentAllocation... allocations) {
        Payment payment = new Payment();
        payment.setBusinessType(businessType);
        payment.setItems(new ArrayList<>(List.of(allocations)));
        return payment;
    }

    private static PaymentAllocation allocation(Long statementId) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setSourceStatementId(statementId);
        return allocation;
    }
}
