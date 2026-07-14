package com.leo.erp.finance.payment.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationRepositoryTest {

    @Mock
    private PaymentAllocationRepository repository;

    @Test
    void sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus_shouldReturnSum() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                100L, "供应商", "已审核")).thenReturn(new BigDecimal("5000.00"));

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                100L, "供应商", "已审核");

        assertThat(result).isEqualByComparingTo("5000.00");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus_shouldReturnZeroWhenNoMatch() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                999L, "供应商", "已审核")).thenReturn(BigDecimal.ZERO);

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(
                999L, "供应商", "已审核");

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId_shouldReturnSumExcludingSpecified() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                200L, "供应商", "已审核", 3L)).thenReturn(new BigDecimal("4000.00"));

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                200L, "供应商", "已审核", 3L);

        assertThat(result).isEqualByComparingTo("4000.00");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId_shouldReturnZeroWhenNoMatch() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                999L, "供应商", "已审核", 1L)).thenReturn(BigDecimal.ZERO);

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                999L, "供应商", "已审核", 1L);

        assertThat(result).isEqualByComparingTo("0");
    }
}
