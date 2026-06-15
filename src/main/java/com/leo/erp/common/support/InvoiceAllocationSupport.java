package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.math.BigDecimal;

public final class InvoiceAllocationSupport {

    private InvoiceAllocationSupport() {
    }

    public static BigDecimal resolveWeightTon(Integer quantity, BigDecimal pieceWeightTon, BigDecimal weightTon) {
        if (weightTon != null && weightTon.compareTo(BigDecimal.ZERO) > 0) {
            return weightTon.setScale(PrecisionConstants.WEIGHT_SCALE, PrecisionConstants.DEFAULT_ROUNDING);
        }
        return TradeItemCalculator.calculateWeightTon(quantity, pieceWeightTon);
    }

    public static BigDecimal calculateTaxAmount(BigDecimal amount, BigDecimal requestedTaxAmount, TaxRateProvider taxRateProvider) {
        BigDecimal taxRate = taxRateProvider.resolveCurrentTaxRate();
        if (taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return requestedTaxAmount == null ? BigDecimal.ZERO : requestedTaxAmount;
        }
        return amount.multiply(taxRate).setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING);
    }

    public static void validateDeclaredAmount(String fieldLabel, BigDecimal requestValue, BigDecimal calculatedValue) {
        if (requestValue != null && requestValue.compareTo(calculatedValue) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "与明细计算结果不一致");
        }
    }

    public record AllocationProgress(BigDecimal weightTon, BigDecimal amount) {
        public static final AllocationProgress EMPTY = new AllocationProgress(BigDecimal.ZERO, BigDecimal.ZERO);

        public AllocationProgress merge(AllocationProgress other) {
            return new AllocationProgress(weightTon.add(other.weightTon), amount.add(other.amount));
        }
    }
}
