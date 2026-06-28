package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthResponseTest {

    @Test
    void recordAccessors() {
        HealthCheckResponse db = HealthCheckResponse.up();
        HealthCheckResponse redis = HealthCheckResponse.up();
        HealthCheckResponse disk = HealthCheckResponse.disk("UP", 50L, 100L);

        HealthResponse response = new HealthResponse(
                "UP", "leo", "0.1.0", "abc123", "2024-01-01T00:00:00",
                db, redis, disk
        );

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.app()).isEqualTo("leo");
        assertThat(response.version()).isEqualTo("0.1.0");
        assertThat(response.traceId()).isEqualTo("abc123");
        assertThat(response.timestamp()).isEqualTo("2024-01-01T00:00:00");
        assertThat(response.db()).isEqualTo(db);
        assertThat(response.redis()).isEqualTo(redis);
        assertThat(response.disk()).isEqualTo(disk);
    }

    @Test
    void recordEquality() {
        HealthCheckResponse db = HealthCheckResponse.up();
        HealthCheckResponse redis = HealthCheckResponse.down();
        HealthCheckResponse disk = HealthCheckResponse.up();

        HealthResponse a = new HealthResponse("UP", "leo", "0.1.0", "t1", "ts", db, redis, disk);
        HealthResponse b = new HealthResponse("UP", "leo", "0.1.0", "t1", "ts", db, redis, disk);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        HealthResponse response = new HealthResponse(
                "UP", "leo", "0.1.0", "abc", "ts",
                HealthCheckResponse.up(), HealthCheckResponse.up(), HealthCheckResponse.up()
        );
        assertThat(response.toString()).contains("UP", "leo", "0.1.0");
    }
}
