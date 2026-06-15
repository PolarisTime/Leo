package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;

import java.math.BigDecimal;

public final class StatementBalanceRule {

    private StatementBalanceRule() {
    }

    public static Balance resolve(BigDecimal sourceAmount,
                                  BigDecimal settledAmount,
                                  String settledAmountName,
                                  String overSettledMessage) {
        BigDecimal normalizedSourceAmount = TradeItemCalculator.scaleAmount(
                TradeItemCalculator.safeBigDecimal(sourceAmount)
        );
        BigDecimal normalizedSettledAmount = TradeItemCalculator.scaleAmount(
                TradeItemCalculator.safeBigDecimal(settledAmount)
        );
        if (normalizedSettledAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, settledAmountName + "不能为负数");
        }
        if (normalizedSettledAmount.compareTo(normalizedSourceAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, overSettledMessage);
        }
        return new Balance(
                normalizedSourceAmount,
                normalizedSettledAmount,
                TradeItemCalculator.scaleAmount(normalizedSourceAmount.subtract(normalizedSettledAmount).max(BigDecimal.ZERO))
        );
    }

    public record Balance(BigDecimal sourceAmount, BigDecimal settledAmount, BigDecimal closingAmount) {
    }
}
