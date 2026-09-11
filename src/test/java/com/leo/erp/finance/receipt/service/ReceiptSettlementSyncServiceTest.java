package com.leo.erp.finance.receipt.service;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ReceiptSettlementSyncServiceTest {

    @Mock
    private StatementSettlementSyncCommand statementSettlementSyncCommand;

    private ReceiptSettlementSyncService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptSettlementSyncService(statementSettlementSyncCommand);
    }

    private ReceiptAllocation allocation(Long legacyId, Long typedId) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setSourceStatementId(legacyId);
        allocation.setSourceCustomerStatementId(typedId);
        return allocation;
    }

    private Receipt receipt(List<ReceiptAllocation> items) {
        Receipt receipt = new Receipt();
        receipt.setItems(items);
        return receipt;
    }

    @Test
    void captureOriginalAllocationStatementIds_shouldPreferTypedAndSkipNull() {
        Receipt receipt = receipt(List.of(
                allocation(10L, null),
                allocation(5L, 20L),
                allocation(null, null)));

        service.captureOriginalAllocationStatementIds(receipt);

        assertThat(receipt.getOriginalAllocationStatementIds()).containsExactly(10L, 20L);
    }

    @Test
    void resolveLegacySourceStatementId_shouldReturnOnlyForSingleItem() {
        assertThat(service.resolveLegacySourceStatementId(receipt(List.of(allocation(5L, null)))))
                .isEqualTo(5L);
        assertThat(service.resolveLegacySourceStatementId(receipt(List.of(
                allocation(5L, null), allocation(6L, null)))))
                .isNull();
        assertThat(service.resolveLegacySourceStatementId(receipt(List.of())))
                .isNull();
    }

    @Test
    void syncCustomerStatements_shouldSyncUnionOfOriginalAndCurrent() {
        Receipt receipt = receipt(List.of(allocation(1L, null), allocation(2L, null)));
        receipt.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(1L)));

        service.syncCustomerStatements(receipt);

        verify(statementSettlementSyncCommand).syncCustomerStatement(1L);
        verify(statementSettlementSyncCommand).syncCustomerStatement(2L);
        verifyNoMoreInteractions(statementSettlementSyncCommand);
    }

    @Test
    void syncCustomerStatements_shouldSkipNullStatementIds() {
        Receipt receipt = receipt(List.of(allocation(null, null)));
        receipt.setOriginalAllocationStatementIds(new LinkedHashSet<>(List.of(1L)));

        service.syncCustomerStatements(receipt);

        verify(statementSettlementSyncCommand).syncCustomerStatement(1L);
        verifyNoMoreInteractions(statementSettlementSyncCommand);
    }
}
