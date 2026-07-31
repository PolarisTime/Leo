package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.web.dto.HealthResponse;
import com.leo.erp.common.web.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @PublicAccess
    @GetMapping(ApiVersion.V2_PREFIX + "/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse health = healthService.health();
        return ResponseEntity.status(statusOf(health)).body(health);
    }

    private HttpStatus statusOf(HealthResponse health) {
        return healthService.isUp(health) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    }
}
