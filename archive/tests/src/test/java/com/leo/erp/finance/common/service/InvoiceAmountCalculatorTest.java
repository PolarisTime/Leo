package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceAmountCalculatorTest {

    @Test
    void shouldResolveAmountAndDelegateTaxAmount() {
        TaxAmountCalculator taxAmountCalculator = mock(TaxAmountCalculator.class);
        InvoiceAmountCalculator calculator = new InvoiceAmountCalculator(taxAmountCalculator);

        when(taxAmountCalculator.calculate(new BigDecimal("1000.00"), new BigDecimal("50.00")))
                .thenReturn(new BigDecimal("130.00"));

        InvoiceAmountCalculator.InvoiceAmounts amounts = calculator.resolve(
                "开票",
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("50.00")
        );

        assertThat(amounts.amount()).isEqualByComparingTo("1000.00");
        assertThat(amounts.taxAmount()).isEqualByComparingTo("130.00");
    }

    @Test
    void shouldRejectDeclaredAmountMismatch() {
        TaxAmountCalculator taxAmountCalculator = mock(TaxAmountCalculator.class);
        InvoiceAmountCalculator calculator = new InvoiceAmountCalculator(taxAmountCalculator);

        assertThatThrownBy(() -> calculator.resolve(
                "开票",
                new BigDecimal("1000.00"),
                new BigDecimal("999.99"),
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开票与明细计算结果不一致");
    }
}
