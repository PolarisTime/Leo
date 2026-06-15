package com.leo.erp.statement.service;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementBalanceRuleTest {

    @Test
    void shouldResolveClosingAmount() {
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolve(
                new BigDecimal("1000.005"),
                new BigDecimal("250.004"),
                "已结算金额",
                "总额不能低于已结算金额"
        );

        assertThat(balance.sourceAmount()).isEqualByComparingTo("1000.01");
        assertThat(balance.settledAmount()).isEqualByComparingTo("250.00");
        assertThat(balance.closingAmount()).isEqualByComparingTo("750.01");
    }

    @Test
    void shouldDefaultNullSettledAmountToZero() {
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolve(
                new BigDecimal("1000.00"),
                null,
                "已结算金额",
                "总额不能低于已结算金额"
        );

        assertThat(balance.settledAmount()).isEqualByComparingTo("0.00");
        assertThat(balance.closingAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldRejectNegativeSettledAmount() {
        assertThatThrownBy(() -> StatementBalanceRule.resolve(
                new BigDecimal("1000.00"),
                new BigDecimal("-0.01"),
                "已结算金额",
                "总额不能低于已结算金额"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算金额不能为负数");
    }

    @Test
    void shouldRejectOverSettledAmount() {
        assertThatThrownBy(() -> StatementBalanceRule.resolve(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.01"),
                "已结算金额",
                "总额不能低于已结算金额"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("总额不能低于已结算金额");
    }
}
