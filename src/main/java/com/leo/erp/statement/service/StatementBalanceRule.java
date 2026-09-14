package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;

import java.math.BigDecimal;

public final class StatementBalanceRule {

    private StatementBalanceRule() {
    }

    /**
     * 蓝字对账余额规则：销售金额非负、收款金额非负且不超过销售金额，closing 不小于 0。
     */
    public static Balance resolve(BigDecimal sourceAmount,
                                  BigDecimal settledAmount,
                                  String settledAmountName,
                                  String overSettledMessage) {
        return resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_BLUE,
                sourceAmount,
                settledAmount,
                settledAmountName,
                overSettledMessage
        );
    }

    /**
     * 方向感知的余额规则：
     * <ul>
     *   <li>蓝字：sourceAmount 非负、settledAmount 非负且不超过 sourceAmount，closing 取
     *       {@code max(source - settled, 0)}；</li>
     *   <li>红字：sourceAmount 必须为负（冲销），不参与收款核销（settledAmount 必须为 0），
     *       closing 等于 sourceAmount（负值），不做 {@code max(ZERO)} 截断。</li>
     * </ul>
     */
    public static Balance resolveForDirection(String direction,
                                              BigDecimal sourceAmount,
                                              BigDecimal settledAmount,
                                              String settledAmountName,
                                              String overSettledMessage) {
        BigDecimal normalizedSourceAmount = TradeItemCalculator.scaleAmount(
                TradeItemCalculator.safeBigDecimal(sourceAmount)
        );
        BigDecimal normalizedSettledAmount = TradeItemCalculator.scaleAmount(
                TradeItemCalculator.safeBigDecimal(settledAmount)
        );
        if (StatusConstants.STATEMENT_DIRECTION_RED.equals(direction)) {
            if (normalizedSourceAmount.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "红字对账单销售金额必须为负数");
            }
            if (normalizedSettledAmount.compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, settledAmountName + "不能为非零，红字对账单不参与收款核销");
            }
            return new Balance(normalizedSourceAmount, normalizedSettledAmount, normalizedSourceAmount);
        }
        if (normalizedSourceAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单销售金额不能为负数");
        }
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

    /**
     * 红字冲销余额：销售金额为负，无收款，closing 等于负销售金额。
     */
    public static Balance resolveReversal(BigDecimal sourceAmount) {
        return resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_RED,
                sourceAmount,
                BigDecimal.ZERO,
                "红字对账单收款金额",
                "红字对账单不参与收款核销"
        );
    }

    public record Balance(BigDecimal sourceAmount, BigDecimal settledAmount, BigDecimal closingAmount) {
    }
}
