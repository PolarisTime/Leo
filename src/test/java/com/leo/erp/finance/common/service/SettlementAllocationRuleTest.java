package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementAllocationRuleTest {

    @Test
    void shouldNormalizePositiveAmount() {
        assertThat(SettlementAllocationRule.requirePositiveAmount(new BigDecimal("12.345"), 2))
                .isEqualByComparingTo("12.35");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> SettlementAllocationRule.requirePositiveAmount(BigDecimal.ZERO, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行核销金额必须大于0");
    }

    @Test
    void shouldSkipCompleteCheckForNonSettledStatus() {
        SettlementAllocationRule.requireCompleteForSettledStatus(
                StatusConstants.DRAFT,
                StatusConstants.PAID,
                true,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                "必须填写核销明细",
                "金额必须相等"
        );
    }

    @Test
    void shouldRejectSettledStatusWithoutAllocations() {
        assertThatThrownBy(() -> SettlementAllocationRule.requireCompleteForSettledStatus(
                StatusConstants.PAID,
                StatusConstants.PAID,
                true,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                "必须填写核销明细",
                "金额必须相等"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须填写核销明细");
    }

    @Test
    void shouldRejectSettledStatusWithMismatchedAmount() {
        assertThatThrownBy(() -> SettlementAllocationRule.requireCompleteForSettledStatus(
                StatusConstants.PAID,
                StatusConstants.PAID,
                false,
                new BigDecimal("99.00"),
                new BigDecimal("100.00"),
                "必须填写核销明细",
                "金额必须相等"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须相等");
    }
}
