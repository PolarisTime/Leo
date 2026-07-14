package com.leo.erp.statement.service;

import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatementSettlementSyncServiceTest {

    @Test
    void syncSupplierStatementShouldCalculateClosingAmount() {
        SupplierStatementRepository supplierRepo = mock(SupplierStatementRepository.class);
        when(supplierRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentAllocationRepository paymentRepo = mock(PaymentAllocationRepository.class);
        when(paymentRepo.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                eq(1L), eq("供应商"), eq(StatementSettlementSyncService.PAYMENT_STATUS_SETTLED)
        )).thenReturn(new BigDecimal("3000.00"));

        StatementSettlementSyncService service = new StatementSettlementSyncService(
                supplierRepo, mock(CustomerStatementRepository.class),
                mock(FreightStatementRepository.class), paymentRepo,
                mock(ReceiptAllocationRepository.class)
        );

        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        statement.setPurchaseAmount(new BigDecimal("10000.00"));

        SupplierStatement result = service.syncSupplierStatement(statement);

        assertThat(result.getPaymentAmount()).isEqualByComparingTo("3000.00");
        assertThat(result.getClosingAmount()).isEqualByComparingTo("7000.00");
        verify(supplierRepo).save(statement);
    }

    @Test
    void syncCustomerStatementShouldCalculateClosingAmount() {
        CustomerStatementRepository customerRepo = mock(CustomerStatementRepository.class);
        when(customerRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptAllocationRepository receiptRepo = mock(ReceiptAllocationRepository.class);
        when(receiptRepo.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                eq(1L), eq(StatementSettlementSyncService.RECEIPT_STATUS_SETTLED)
        )).thenReturn(new BigDecimal("5000.00"));

        StatementSettlementSyncService service = new StatementSettlementSyncService(
                mock(SupplierStatementRepository.class), customerRepo,
                mock(FreightStatementRepository.class),
                mock(PaymentAllocationRepository.class), receiptRepo
        );

        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setSalesAmount(new BigDecimal("8000.00"));

        CustomerStatement result = service.syncCustomerStatement(statement);

        assertThat(result.getReceiptAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.getClosingAmount()).isEqualByComparingTo("3000.00");
        verify(customerRepo).save(statement);
    }

    @Test
    void syncFreightStatementShouldCalculateUnpaidAmount() {
        FreightStatementRepository freightRepo = mock(FreightStatementRepository.class);
        when(freightRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentAllocationRepository paymentRepo = mock(PaymentAllocationRepository.class);
        when(paymentRepo.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                eq(1L), eq("物流商"), eq(StatementSettlementSyncService.PAYMENT_STATUS_SETTLED)
        )).thenReturn(new BigDecimal("1500.00"));

        StatementSettlementSyncService service = new StatementSettlementSyncService(
                mock(SupplierStatementRepository.class),
                mock(CustomerStatementRepository.class), freightRepo,
                paymentRepo, mock(ReceiptAllocationRepository.class)
        );

        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        statement.setTotalFreight(new BigDecimal("5000.00"));

        FreightStatement result = service.syncFreightStatement(statement);

        assertThat(result.getPaidAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.getUnpaidAmount()).isEqualByComparingTo("3500.00");
        verify(freightRepo).save(statement);
    }

    @Test
    void syncSupplierStatementShouldNotReturnNegativeClosingAmount() {
        SupplierStatementRepository supplierRepo = mock(SupplierStatementRepository.class);
        when(supplierRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentAllocationRepository paymentRepo = mock(PaymentAllocationRepository.class);
        when(paymentRepo.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                anyLong(), anyString(), anyString()
        )).thenReturn(new BigDecimal("15000.00"));

        StatementSettlementSyncService service = new StatementSettlementSyncService(
                supplierRepo, mock(CustomerStatementRepository.class),
                mock(FreightStatementRepository.class), paymentRepo,
                mock(ReceiptAllocationRepository.class)
        );

        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        statement.setPurchaseAmount(new BigDecimal("10000.00"));

        SupplierStatement result = service.syncSupplierStatement(statement);

        assertThat(result.getClosingAmount()).isEqualByComparingTo("0.00");
    }
}
