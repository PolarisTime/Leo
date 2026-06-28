package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.dto.HealthCheckResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import com.leo.erp.common.web.service.HealthService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final HealthService healthService = mock(HealthService.class);
    private final HealthController controller = new HealthController(healthService);

    @Test
    void shouldReturnHealthResponse() {
        HealthResponse healthResponse = new HealthResponse(
                "UP", "leo", "0.1.0", "trace-123", "2026-01-01 00:00:00",
                HealthCheckResponse.up(),
                HealthCheckResponse.up(),
                HealthCheckResponse.disk("UP", 100L, 500L)
        );
        when(healthService.health()).thenReturn(healthResponse);

        ApiResponse<HealthResponse> response = controller.health();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(healthResponse);
        assertThat(response.data().status()).isEqualTo("UP");
    }

    @Test
    void shouldReturnDegradedStatusWhenChecksFail() {
        HealthResponse healthResponse = new HealthResponse(
                "DEGRADED", "leo", "0.1.0", "", "2026-01-01 00:00:00",
                HealthCheckResponse.down(),
                HealthCheckResponse.up(),
                HealthCheckResponse.disk("UP", 100L, 500L)
        );
        when(healthService.health()).thenReturn(healthResponse);

        ApiResponse<HealthResponse> response = controller.health();

        assertThat(response.data().status()).isEqualTo("DEGRADED");
    }
}
