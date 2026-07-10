package com.leo.erp.auth.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RevokeReasonTest {

    @Test
    void shouldHaveCorrectEnumValues() {
        assertThat(RevokeReason.values()).hasSize(5);
        assertThat(RevokeReason.valueOf("MANUAL")).isEqualTo(RevokeReason.MANUAL);
        assertThat(RevokeReason.valueOf("CONCURRENT_LIMIT")).isEqualTo(RevokeReason.CONCURRENT_LIMIT);
        assertThat(RevokeReason.valueOf("EXPIRED")).isEqualTo(RevokeReason.EXPIRED);
        assertThat(RevokeReason.valueOf("REUSE_DETECTED")).isEqualTo(RevokeReason.REUSE_DETECTED);
        assertThat(RevokeReason.valueOf("PASSWORD_CHANGED")).isEqualTo(RevokeReason.PASSWORD_CHANGED);
    }

    @Test
    void shouldSupportEqualityComparison() {
        assertThat(RevokeReason.MANUAL).isEqualTo(RevokeReason.MANUAL);
        assertThat(RevokeReason.CONCURRENT_LIMIT).isEqualTo(RevokeReason.CONCURRENT_LIMIT);
        assertThat(RevokeReason.MANUAL).isNotEqualTo(RevokeReason.EXPIRED);
    }
}
