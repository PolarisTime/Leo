package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StatementSettlementMutationGuardTest {

    @Test
    void shouldRejectSupplierMutationWhenSettledPaymentAllocationExists() {
        PaymentAllocationRepository paymentRepository = mock(PaymentAllocationRepository.class);
        when(paymentRepository.countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                11L,
                "供应商",
                StatementSettlementSyncService.PAYMENT_STATUS_SETTLED
        )).thenReturn(1L);
        StatementSettlementMutationGuard guard = new StatementSettlementMutationGuard(
                paymentRepository,
                mock(ReceiptAllocationRepository.class)
        );

        assertThatThrownBy(() -> guard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.SUPPLIER,
                11L,
                "反确认"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单已存在已付款核销")
                .hasMessageContaining("请先反核销");
    }

    @Test
    void shouldRejectCustomerMutationWhenSettledReceiptAllocationExists() {
        ReceiptAllocationRepository receiptRepository = mock(ReceiptAllocationRepository.class);
        when(receiptRepository.countSettledAllocationsByStatementIdAndStatus(
                21L,
                StatementSettlementSyncService.RECEIPT_STATUS_SETTLED
        )).thenReturn(1L);
        StatementSettlementMutationGuard guard = new StatementSettlementMutationGuard(
                mock(PaymentAllocationRepository.class),
                receiptRepository
        );

        assertThatThrownBy(() -> guard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.CUSTOMER,
                21L,
                "删除"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单已存在已收款核销")
                .hasMessageContaining("请先反核销");
    }

    @Test
    void shouldRejectFreightMutationWhenSettledPaymentAllocationExists() {
        PaymentAllocationRepository paymentRepository = mock(PaymentAllocationRepository.class);
        when(paymentRepository.countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                31L,
                "物流商",
                StatementSettlementSyncService.PAYMENT_STATUS_SETTLED
        )).thenReturn(2L);
        StatementSettlementMutationGuard guard = new StatementSettlementMutationGuard(
                paymentRepository,
                mock(ReceiptAllocationRepository.class)
        );

        assertThatThrownBy(() -> guard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.FREIGHT,
                31L,
                "修改往来单位或来源"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单已存在已付款核销")
                .hasMessageContaining("请先反核销");
    }

    @Test
    void shouldAllowMutationWhenNoSettledAllocationExists() {
        StatementSettlementMutationGuard guard = new StatementSettlementMutationGuard(
                mock(PaymentAllocationRepository.class),
                mock(ReceiptAllocationRepository.class)
        );

        assertThatCode(() -> guard.assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.SUPPLIER,
                11L,
                "反确认"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipAllocationLookupWhenFinancialLinkageIsUnchanged() {
        PaymentAllocationRepository paymentRepository = mock(PaymentAllocationRepository.class);
        ReceiptAllocationRepository receiptRepository = mock(ReceiptAllocationRepository.class);
        StatementSettlementMutationGuard guard = new StatementSettlementMutationGuard(
                paymentRepository,
                receiptRepository
        );

        guard.assertFinancialLinkageMutationAllowed(
                StatementSettlementMutationGuard.StatementType.SUPPLIER,
                11L,
                false
        );

        verifyNoInteractions(paymentRepository, receiptRepository);
    }
}
