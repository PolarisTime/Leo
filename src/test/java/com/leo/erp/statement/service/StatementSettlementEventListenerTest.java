package com.leo.erp.statement.service;

import com.leo.erp.finance.payment.service.PaymentSettledEvent;
import com.leo.erp.finance.receipt.service.ReceiptSettledEvent;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class StatementSettlementEventListenerTest {

    private final StatementSettlementSyncService syncService = mock(StatementSettlementSyncService.class);
    private final SupplierStatementRepository supplierRepo = mock(SupplierStatementRepository.class);
    private final FreightStatementRepository freightRepo = mock(FreightStatementRepository.class);
    private final CustomerStatementRepository customerRepo = mock(CustomerStatementRepository.class);

    private final StatementSettlementEventListener listener = new StatementSettlementEventListener(
            syncService, supplierRepo, freightRepo, customerRepo);

    @Test
    void shouldSyncSupplierStatementOnPaymentSettled() {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(100L);
        when(supplierRepo.findByIdAndDeletedFlagFalse(100L)).thenReturn(Optional.of(statement));

        listener.onPaymentSettled(new PaymentSettledEvent(100L, "供应商"));

        verify(syncService).syncSupplierStatement(statement);
    }

    @Test
    void shouldSyncFreightStatementOnFreightPaymentSettled() {
        FreightStatement statement = new FreightStatement();
        statement.setId(200L);
        when(freightRepo.findByIdAndDeletedFlagFalse(200L)).thenReturn(Optional.of(statement));

        listener.onPaymentSettled(new PaymentSettledEvent(200L, "物流商"));

        verify(syncService).syncFreightStatement(statement);
    }

    @Test
    void shouldSyncCustomerStatementOnReceiptSettled() {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(300L);
        when(customerRepo.findByIdAndDeletedFlagFalse(300L)).thenReturn(Optional.of(statement));

        listener.onReceiptSettled(new ReceiptSettledEvent(300L));

        verify(syncService).syncCustomerStatement(statement);
    }

    @Test
    void shouldSkipNullStatementId() {
        listener.onPaymentSettled(new PaymentSettledEvent(null, "供应商"));
        listener.onReceiptSettled(new ReceiptSettledEvent(null));

        verifyNoInteractions(supplierRepo);
        verifyNoInteractions(customerRepo);
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldSkipUnknownBusinessType() {
        listener.onPaymentSettled(new PaymentSettledEvent(1L, "未知类型"));

        verifyNoInteractions(supplierRepo);
        verifyNoInteractions(freightRepo);
    }

    @Test
    void shouldSkipWhenStatementNotFound() {
        when(supplierRepo.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        listener.onPaymentSettled(new PaymentSettledEvent(999L, "供应商"));

        verify(supplierRepo).findByIdAndDeletedFlagFalse(999L);
        verifyNoInteractions(syncService);
    }
}
