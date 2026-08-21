package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;

import java.math.BigDecimal;

public final class SettlementAllocationRule {

    private SettlementAllocationRule() {
    }

    public static BigDecimal requirePositiveAmount(BigDecimal allocatedAmount, int lineNo) {
        BigDecimal normalized = TradeItemCalculator.safeBigDecimal(allocatedAmount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行核销金额必须大于0");
        }
        return TradeItemCalculator.scaleAmount(normalized);
    }

    public static void requireCompleteForSettledStatus(String nextStatus,
                                                       String settledStatus,
                                                       boolean allocationEmpty,
                                                       BigDecimal totalAllocatedAmount,
                                                       BigDecimal documentAmount,
                                                       String emptyMessage,
                                                       String mismatchMessage) {
        if (!settledStatus.equals(nextStatus)) {
            return;
        }
        if (allocationEmpty) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, emptyMessage);
        }
        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(documentAmount)) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, mismatchMessage);
        }
    }

    /**
     * 校验追加核销后累计金额不超过对账单上限，收付款两侧共用同一规则骨架。
     */
    public static void assertWithinAllocatedLimit(BigDecimal settledAllocatedAmount,
                                                  BigDecimal incomingAmount,
                                                  BigDecimal statementCapAmount,
                                                  String exceedMessage) {
        BigDecimal nextSettledAmount = TradeItemCalculator.safeBigDecimal(settledAllocatedAmount)
                .add(incomingAmount);
        if (nextSettledAmount.compareTo(statementCapAmount) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exceedMessage);
        }
    }
}
