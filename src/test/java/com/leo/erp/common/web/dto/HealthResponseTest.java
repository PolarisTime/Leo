package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthResponseTest {

    @Test
    void recordAccessors() {
        HealthResponse response = new HealthResponse("UP", "2024-01-01T00:00:00");

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.timestamp()).isEqualTo("2024-01-01T00:00:00");
    }

    @Test
    void recordEquality() {
        HealthResponse a = new HealthResponse("UP", "ts");
        HealthResponse b = new HealthResponse("UP", "ts");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        HealthResponse response = new HealthResponse("UP", "ts");

        assertThat(response.toString()).contains("UP", "ts");
        assertThat(response.toString())
                .doesNotContain("app", "version", "traceId", "db", "redis", "disk");
    }
}
