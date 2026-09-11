package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.statement.api.StatementSettlementSyncCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementSyncServiceTest {

    @Mock
    private StatementSettlementSyncCommand statementSettlementSyncCommand;

    private PaymentSettlementSyncService service;

    @BeforeEach
    void setUp() {
        service = new PaymentSettlementSyncService(statementSettlementSyncCommand);
    }

    private PaymentAllocation allocation(Long legacyId, Long typedId) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setSourceStatementId(legacyId);
        allocation.setSourceFreightStatementId(typedId);
        return allocation;
    }

    private Payment payment(String businessType, List<PaymentAllocation> items) {
        Payment payment = new Payment();
        payment.setBusinessType(businessType);
        payment.setItems(items);
        return payment;
    }

    @Test
    void captureOriginalAllocationState_shouldIgnoreNonFreightBusinessType() {
        Payment payment = payment("供应商", List.of(allocation(10L, null)));

        service.captureOriginalAllocationState(payment);

        assertThat(payment.getOriginalBusinessType()).isEqualTo("供应商");
        assertThat(payment.getOriginalAllocationStatementIds()).isEmpty();
    }

    @Test
    void captureOriginalAllocationState_shouldPreferTypedAndSkipNull() {
        Payment payment = payment("物流商", List.of(
                allocation(10L, null),
                allocation(5L, 20L),
                allocation(null, null)));

        service.captureOriginalAllocationState(payment);

        assertThat(payment.getOriginalAllocationStatementIds()).containsExactly(10L, 20L);
    }

    @Test
    void resolveLegacySourceStatementId_shouldReturnOnlyForSingleItem() {
        assertThat(service.resolveLegacySourceStatementId(payment("物流商", List.of(allocation(5L, null)))))
                .isEqualTo(5L);
        assertThat(service.resolveLegacySourceStatementId(payment("物流商", List.of(
                allocation(5L, null), allocation(6L, null)))))
                .isNull();
        assertThat(service.resolveLegacySourceStatementId(payment("物流商", List.of())))
                .isNull();
    }

    @Test
    void syncLinkedStatements_shouldSyncFreightStatementsFromOriginalAndCurrent() {
        Payment payment = payment("物流商", List.of(allocation(2L, null)));
        payment.setOriginalBusinessType("物流商");
        payment.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(1L)));

        service.syncLinkedStatements(payment);

        verify(statementSettlementSyncCommand).syncFreightStatement(1L);
        verify(statementSettlementSyncCommand).syncFreightStatement(2L);
        verifyNoMoreInteractions(statementSettlementSyncCommand);
    }

    @Test
    void syncLinkedStatements_shouldSkipNonFreightAndNullStatementIds() {
        Payment payment = payment("供应商", List.of(allocation(9L, null)));
        payment.setOriginalBusinessType("物流商");
        payment.setOriginalAllocationStatementIds(new LinkedHashSet<>());

        service.syncLinkedStatements(payment);

        verifyNoInteractions(statementSettlementSyncCommand);
    }

    @Test
    void syncLinkedStatements_shouldSkipNullCurrentStatementId() {
        Payment payment = payment("物流商", List.of(allocation(null, null)));
        payment.setOriginalBusinessType("物流商");
        payment.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(1L)));

        service.syncLinkedStatements(payment);

        verify(statementSettlementSyncCommand).syncFreightStatement(1L);
        verifyNoMoreInteractions(statementSettlementSyncCommand);
    }
}
