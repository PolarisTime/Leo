package com.leo.erp.finance.common.service;

import com.leo.erp.common.support.InvoiceAllocationSupport;
import com.leo.erp.common.support.TaxRateProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TaxAmountCalculator {

    private final TaxRateProvider taxRateProvider;

    public TaxAmountCalculator(TaxRateProvider taxRateProvider) {
        this.taxRateProvider = taxRateProvider;
    }

    BigDecimal calculate(BigDecimal amount, BigDecimal declaredTaxAmount) {
        return InvoiceAllocationSupport.calculateTaxAmount(amount, declaredTaxAmount, taxRateProvider);
    }
}
