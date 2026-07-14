package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckResponseTest {

    @Test
    void up_returnsUpStatus() {
        HealthCheckResponse response = HealthCheckResponse.up();
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.freeGb()).isZero();
        assertThat(response.totalGb()).isZero();
        assertThat(response.isUp()).isTrue();
    }

    @Test
    void down_returnsDownStatus() {
        HealthCheckResponse response = HealthCheckResponse.down();
        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.freeGb()).isZero();
        assertThat(response.totalGb()).isZero();
        assertThat(response.isUp()).isFalse();
    }

    @Test
    void disk_returnsCustomValues() {
        HealthCheckResponse response = HealthCheckResponse.disk("UP", 50L, 100L);
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.freeGb()).isEqualTo(50L);
        assertThat(response.totalGb()).isEqualTo(100L);
        assertThat(response.isUp()).isTrue();
    }

    @Test
    void isUp_falseWhenDown() {
        HealthCheckResponse response = new HealthCheckResponse("DOWN", 10L, 100L);
        assertThat(response.isUp()).isFalse();
    }

    @Test
    void recordEquality() {
        HealthCheckResponse a = HealthCheckResponse.up();
        HealthCheckResponse b = HealthCheckResponse.up();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toString_containsFields() {
        HealthCheckResponse response = HealthCheckResponse.up();
        assertThat(response.toString()).contains("UP");
    }
}
