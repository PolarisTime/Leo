package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementBalanceRuleTest {

    @Test
    void blue_shouldComputeClosingAndRejectOverSettlement() {
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_BLUE,
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                "客户对账单收款金额",
                "客户对账单销售金额不能低于已收款金额"
        );

        assertThat(balance.sourceAmount()).isEqualByComparingTo("5000.00");
        assertThat(balance.settledAmount()).isEqualByComparingTo("1000.00");
        assertThat(balance.closingAmount()).isEqualByComparingTo("4000.00");

        assertThatThrownBy(() -> StatementBalanceRule.resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_BLUE,
                new BigDecimal("5000.00"),
                new BigDecimal("6000.00"),
                "客户对账单收款金额",
                "客户对账单销售金额不能低于已收款金额"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void blue_shouldRejectNegativeSourceAndNegativeSettlement() {
        assertThatThrownBy(() -> StatementBalanceRule.resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_BLUE,
                new BigDecimal("-1.00"),
                BigDecimal.ZERO,
                "客户对账单收款金额",
                "over"
        )).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> StatementBalanceRule.resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_BLUE,
                new BigDecimal("100.00"),
                new BigDecimal("-0.01"),
                "客户对账单收款金额",
                "over"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void reversal_shouldAllowNegativeSourceAndSkipMaxZero() {
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolveReversal(new BigDecimal("-4000.00"));

        assertThat(balance.sourceAmount()).isEqualByComparingTo("-4000.00");
        assertThat(balance.settledAmount()).isEqualByComparingTo("0");
        assertThat(balance.closingAmount()).isEqualByComparingTo("-4000.00");
    }

    @Test
    void reversal_shouldRejectPositiveSource() {
        assertThatThrownBy(() -> StatementBalanceRule.resolveReversal(new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reversal_shouldRejectNonZeroSettlement() {
        assertThatThrownBy(() -> StatementBalanceRule.resolveForDirection(
                StatusConstants.STATEMENT_DIRECTION_RED,
                new BigDecimal("-4000.00"),
                new BigDecimal("1.00"),
                "红字对账单收款金额",
                "红字对账单不参与收款核销"
        )).isInstanceOf(BusinessException.class);
    }
}
