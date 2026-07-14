package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class PrecisionConstantsTest {

    @Test
    void shouldHaveCorrectWeightScale() {
        assertThat(PrecisionConstants.WEIGHT_SCALE).isEqualTo(8);
    }

    @Test
    void shouldHaveCorrectDisplayWeightScale() {
        assertThat(PrecisionConstants.DISPLAY_WEIGHT_SCALE).isEqualTo(3);
    }

    @Test
    void shouldHaveCorrectAmountScale() {
        assertThat(PrecisionConstants.AMOUNT_SCALE).isEqualTo(2);
    }

    @Test
    void shouldHaveCorrectTaxRateScale() {
        assertThat(PrecisionConstants.TAX_RATE_SCALE).isEqualTo(4);
    }

    @Test
    void shouldHaveHalfUpRoundingMode() {
        assertThat(PrecisionConstants.DEFAULT_ROUNDING).isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    void shouldHaveCorrectIdPrefixLength() {
        assertThat(PrecisionConstants.ID_PREFIX_LENGTH).isEqualTo(8);
    }
}
