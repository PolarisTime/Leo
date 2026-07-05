package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import com.leo.erp.common.web.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final HealthService healthService = mock(HealthService.class);
    private final HealthController controller = new HealthController(healthService);

    @Test
    void shouldReturnOkWhenHealthIsUp() {
        HealthResponse healthResponse = new HealthResponse("UP", "2026-01-01 00:00:00");
        when(healthService.health()).thenReturn(healthResponse);
        when(healthService.isUp(healthResponse)).thenReturn(true);

        ResponseEntity<ApiResponse<HealthResponse>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(0);
        assertThat(response.getBody().data()).isEqualTo(healthResponse);
        assertThat(response.getBody().data().status()).isEqualTo("UP");
    }

    @Test
    void shouldReturnServiceUnavailableWhenHealthIsNotUp() {
        HealthResponse healthResponse = new HealthResponse("DEGRADED", "2026-01-01 00:00:00");
        when(healthService.health()).thenReturn(healthResponse);
        when(healthService.isUp(healthResponse)).thenReturn(false);

        ResponseEntity<ApiResponse<HealthResponse>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo("DEGRADED");
    }
}
