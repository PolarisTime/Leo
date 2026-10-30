package com.leo.erp.market.quotation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目级定尺加价默认值：优先定尺主数据的 12米 加价，取不到才回退常量 30。
 */
class QuoteDefaultsTest {

    @Test
    void usesMasterDataTwelveMeterPremium() {
        assertThat(QuoteDefaults.lengthPremiumOrDefault(Map.of("9米", 0, "12米", 35)))
                .isEqualByComparingTo("35");
    }

    @Test
    void allowsZeroPremiumFromMasterData() {
        // 0 是合法取值（12米 不加价），不能被当成「取不到」而回退 30
        assertThat(QuoteDefaults.lengthPremiumOrDefault(Map.of("12米", 0)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fallsBackToConstantWhenMasterDataEmptyOrMissing() {
        assertThat(QuoteDefaults.lengthPremiumOrDefault(Map.of())).isEqualByComparingTo("30");
        assertThat(QuoteDefaults.lengthPremiumOrDefault(Map.of("9米", 15))).isEqualByComparingTo("30");
        assertThat(QuoteDefaults.lengthPremiumOrDefault(null)).isEqualByComparingTo("30");
    }
}
