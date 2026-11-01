package com.leo.erp.sales.contract.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同额度唯一计算处的边界用例。
 *
 * <p>该计算处同时服务销售订单保存前校验与项目销售汇总两个出口，使用率/超限/文案的边界行为
 * 必须在这里锁定，避免两个出口各写一份公式后给出互相矛盾的数字。</p>
 */
class SalesContractQuotaTest {

    @Test
    void withoutContract_zerosEveryQuotaFieldAndKeepsMessage() {
        SalesContractQuota quota = SalesContractQuota.withoutContract();

        assertThat(quota.hasContract()).isFalse();
        assertThat(quota.contractAmount()).isEqualByComparingTo("0");
        assertThat(quota.usedAmount()).isEqualByComparingTo("0");
        assertThat(quota.remainingAmount()).isEqualByComparingTo("0");
        assertThat(quota.exceededAmount()).isEqualByComparingTo("0");
        assertThat(quota.contractTonnage()).isEqualByComparingTo("0");
        assertThat(quota.usedTonnage()).isEqualByComparingTo("0");
        assertThat(quota.remainingTonnage()).isEqualByComparingTo("0");
        assertThat(quota.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(quota.amountUsageRate()).isNull();
        assertThat(quota.tonnageUsageRate()).isNull();
        assertThat(quota.message()).isEqualTo("该项目未关联有效销售合同");
    }

    @Test
    void evaluate_usageRateHalvesUpToFourDecimals() {
        SalesContractQuota oneThird = SalesContractQuota.evaluate(
                new BigDecimal("3.00"), new BigDecimal("1.00"),
                new BigDecimal("3.00000000"), new BigDecimal("1.00000000"));

        assertThat(oneThird.amountUsageRate()).isEqualByComparingTo("0.3333");
        assertThat(oneThird.amountUsageRate().scale()).isEqualTo(4);
        assertThat(oneThird.tonnageUsageRate()).isEqualByComparingTo("0.3333");
    }

    @Test
    void evaluate_usageRateRoundsHalfUp() {
        SalesContractQuota quota = SalesContractQuota.evaluate(
                new BigDecimal("8.00"), new BigDecimal("5.00"),
                new BigDecimal("8.00000000"), new BigDecimal("5.00000000"));

        // 5 / 8 = 0.625 -> 0.6250
        assertThat(quota.amountUsageRate()).isEqualByComparingTo("0.6250");
        assertThat(quota.tonnageUsageRate()).isEqualByComparingTo("0.6250");
    }

    @Test
    void evaluate_nullInputs_areTreatedAsZeroAndRatesBecomeNull() {
        SalesContractQuota quota = SalesContractQuota.evaluate(null, null, null, null);

        assertThat(quota.hasContract()).isTrue();
        assertThat(quota.contractAmount()).isEqualByComparingTo("0");
        assertThat(quota.usedAmount()).isEqualByComparingTo("0");
        assertThat(quota.amountUsageRate()).isNull();
        assertThat(quota.tonnageUsageRate()).isNull();
        assertThat(quota.message()).isEqualTo("合同额度充足");
    }

    @Test
    void evaluate_exceededIsClampedAtZeroAndRemainingKeepsNegative() {
        SalesContractQuota quota = SalesContractQuota.evaluate(
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                new BigDecimal("10.00000000"), new BigDecimal("12.00000000"));

        assertThat(quota.remainingAmount()).isEqualByComparingTo("-20.00");
        assertThat(quota.exceededAmount()).isEqualByComparingTo("20.00");
        assertThat(quota.remainingTonnage()).isEqualByComparingTo("-2.00000000");
        assertThat(quota.exceededTonnage()).isEqualByComparingTo("2.00000000");
        assertThat(quota.amountUsageRate()).isEqualByComparingTo("1.2000");
        assertThat(quota.message()).contains("已超出合同额度");
    }
}
