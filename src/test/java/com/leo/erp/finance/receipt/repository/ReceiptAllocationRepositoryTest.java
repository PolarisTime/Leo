package com.leo.erp.finance.receipt.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptAllocationRepositoryTest {

    @Mock
    private ReceiptAllocationRepository repository;

    @Test
    void sumAllocatedAmountBySourceStatementIdAndReceiptStatus_shouldReturnSum() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                100L, "已收款")).thenReturn(new BigDecimal("8000.00"));

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                100L, "已收款");

        assertThat(result).isEqualByComparingTo("8000.00");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndReceiptStatus_shouldReturnZeroWhenNoMatch() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                999L, "已收款")).thenReturn(BigDecimal.ZERO);

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatus(
                999L, "已收款");

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId_shouldReturnSumExcludingSpecified() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                200L, "已收款", 3L)).thenReturn(new BigDecimal("8000.00"));

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                200L, "已收款", 3L);

        assertThat(result).isEqualByComparingTo("8000.00");
    }

    @Test
    void sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId_shouldReturnZeroWhenNoMatch() {
        when(repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                999L, "已收款", 1L)).thenReturn(BigDecimal.ZERO);

        BigDecimal result = repository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                999L, "已收款", 1L);

        assertThat(result).isEqualByComparingTo("0");
    }
}
