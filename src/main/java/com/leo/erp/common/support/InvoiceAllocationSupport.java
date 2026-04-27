package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.company.service.CompanySettingService;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InvoiceAllocationSupport {

    private InvoiceAllocationSupport() {
    }

    public static BigDecimal resolveWeightTon(Integer quantity, BigDecimal pieceWeightTon, BigDecimal weightTon) {
        if (weightTon != null && weightTon.compareTo(BigDecimal.ZERO) > 0) {
            return weightTon.setScale(3, RoundingMode.HALF_UP);
        }
        return TradeItemCalculator.calculateWeightTon(quantity, pieceWeightTon);
    }

    public static BigDecimal calculateTaxAmount(BigDecimal amount, BigDecimal requestedTaxAmount, CompanySettingService companySettingService) {
        BigDecimal taxRate = companySettingService.resolveCurrentTaxRate();
        if (taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return requestedTaxAmount == null ? BigDecimal.ZERO : requestedTaxAmount;
        }
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
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
