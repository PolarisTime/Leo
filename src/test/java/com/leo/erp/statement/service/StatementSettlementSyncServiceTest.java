package com.leo.erp.statement.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.api.StatementSettlementAllocationPort;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * StatementSettlementSyncService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class StatementSettlementSyncServiceTest {

    @Mock
    private CustomerStatementRepository customerStatementRepository;

    @Mock
    private FreightStatementRepository freightStatementRepository;

    @Mock
    private StatementSettlementAllocationPort settlementAllocationPort;

    @InjectMocks
    private StatementSettlementSyncService service;

    @Test
    void syncCustomerStatement_shouldSkipNullId() {
        service.syncCustomerStatement((Long) null);
        verifyNoInteractions(customerStatementRepository);
    }

    @Test
    void syncCustomerStatement_shouldSyncWhenPresent() {
        CustomerStatement stmt = new CustomerStatement();
        stmt.setId(1L);
        stmt.setSalesAmount(new BigDecimal("5000"));
        when(customerStatementRepository.findByIdAndDeletedFlagFalseForSettlementUpdate(1L))
                .thenReturn(Optional.of(stmt));

        service.syncCustomerStatement(1L);

        verify(customerStatementRepository).save(stmt);
    }

    @Test
    void syncCustomerStatement_shouldSkipWhenAbsent() {
        when(customerStatementRepository.findByIdAndDeletedFlagFalseForSettlementUpdate(1L))
                .thenReturn(Optional.empty());

        service.syncCustomerStatement(1L);

        verify(customerStatementRepository).findByIdAndDeletedFlagFalseForSettlementUpdate(1L);
        verify(customerStatementRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(CustomerStatement.class));
    }

    @Test
    void syncFreightStatement_shouldSkipNullId() {
        service.syncFreightStatement((Long) null);
        verifyNoInteractions(freightStatementRepository);
    }

    @Test
    void syncFreightStatement_shouldSyncWhenPresent() {
        FreightStatement stmt = new FreightStatement();
        stmt.setId(2L);
        stmt.setTotalFreight(new BigDecimal("5000"));
        when(freightStatementRepository.findByIdAndDeletedFlagFalseForSettlementUpdate(2L))
                .thenReturn(Optional.of(stmt));
        when(settlementAllocationPort.sumPaymentAmount(2L, "物流商", StatusConstants.AUDITED))
                .thenReturn(new BigDecimal("3000"));

        service.syncFreightStatement(2L);

        verify(freightStatementRepository).save(stmt);
    }

    @Test
    void syncCustomerEntity_shouldSetReceiptAndClosingAndSave() {
        CustomerStatement stmt = new CustomerStatement();
        stmt.setId(1L);
        stmt.setSalesAmount(new BigDecimal("5000"));
        when(settlementAllocationPort.sumReceiptAmount(1L, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal("1000"));
        when(customerStatementRepository.save(stmt)).thenReturn(stmt);

        CustomerStatement result = service.syncCustomerStatement(stmt);

        assertThat(result.getReceiptAmount()).isEqualByComparingTo("1000");
        assertThat(result.getClosingAmount()).isEqualByComparingTo("4000");
        verify(customerStatementRepository).save(stmt);
    }

    @Test
    void syncFreightEntity_shouldSetPaidAndUnpaidAndSave() {
        FreightStatement stmt = new FreightStatement();
        stmt.setId(2L);
        stmt.setTotalFreight(new BigDecimal("5000"));
        when(settlementAllocationPort.sumPaymentAmount(2L, "物流商", StatusConstants.AUDITED))
                .thenReturn(new BigDecimal("3000"));
        when(freightStatementRepository.save(stmt)).thenReturn(stmt);

        FreightStatement result = service.syncFreightStatement(stmt);

        assertThat(result.getPaidAmount()).isEqualByComparingTo("3000");
        assertThat(result.getUnpaidAmount()).isEqualByComparingTo("2000");
        verify(freightStatementRepository).save(stmt);
    }

    @Test
    void resolveCustomerReceiptAmount_shouldReturnZeroForNullId() {
        assertThat(service.resolveCustomerReceiptAmount(null)).isEqualByComparingTo("0");
        verifyNoInteractions(settlementAllocationPort);
    }

    @Test
    void resolveCustomerReceiptAmount_shouldScalePortResult() {
        when(settlementAllocationPort.sumReceiptAmount(1L, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal("1000"));

        assertThat(service.resolveCustomerReceiptAmount(1L)).isEqualByComparingTo("1000");
    }

    @Test
    void resolveCustomerReceiptAmount_shouldReturnZeroWhenPortReturnsNull() {
        when(settlementAllocationPort.sumReceiptAmount(1L, StatusConstants.AUDITED)).thenReturn(null);

        assertThat(service.resolveCustomerReceiptAmount(1L)).isEqualByComparingTo("0");
    }
}
