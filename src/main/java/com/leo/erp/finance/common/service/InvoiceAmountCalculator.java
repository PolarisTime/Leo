package com.leo.erp.finance.common.service;

import com.leo.erp.common.support.InvoiceAllocationSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class InvoiceAmountCalculator {

    private final TaxAmountCalculator taxAmountCalculator;

    public InvoiceAmountCalculator(TaxAmountCalculator taxAmountCalculator) {
        this.taxAmountCalculator = taxAmountCalculator;
    }

    public InvoiceAmounts resolve(String fieldLabel,
                                  BigDecimal calculatedAmount,
                                  BigDecimal declaredAmount,
                                  BigDecimal declaredTaxAmount) {
        InvoiceAllocationSupport.validateDeclaredAmount(fieldLabel, declaredAmount, calculatedAmount);
        return new InvoiceAmounts(
                calculatedAmount,
                taxAmountCalculator.calculate(calculatedAmount, declaredTaxAmount)
        );
    }

    public record InvoiceAmounts(BigDecimal amount, BigDecimal taxAmount) {
    }
}
