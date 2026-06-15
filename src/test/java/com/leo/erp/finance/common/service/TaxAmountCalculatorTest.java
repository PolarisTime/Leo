package com.leo.erp.finance.common.service;

import com.leo.erp.common.support.TaxRateProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxAmountCalculatorTest {

    @Test
    void shouldCalculateTaxAmountFromCurrentTaxRate() {
        TaxRateProvider taxRateProvider = mock(TaxRateProvider.class);
        TaxAmountCalculator calculator = new TaxAmountCalculator(taxRateProvider);

        when(taxRateProvider.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));

        BigDecimal taxAmount = calculator.calculate(new BigDecimal("1000.00"), null);

        assertThat(taxAmount).isEqualByComparingTo("130.00");
    }

    @Test
    void shouldUseDeclaredTaxAmountWhenTaxRateIsZero() {
        TaxRateProvider taxRateProvider = mock(TaxRateProvider.class);
        TaxAmountCalculator calculator = new TaxAmountCalculator(taxRateProvider);

        when(taxRateProvider.resolveCurrentTaxRate()).thenReturn(BigDecimal.ZERO);

        BigDecimal taxAmount = calculator.calculate(new BigDecimal("1000.00"), new BigDecimal("50.00"));

        assertThat(taxAmount).isEqualByComparingTo("50.00");
    }
}
