package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.dto.HealthResponse;
import com.leo.erp.common.web.service.HealthService;
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

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(healthService.health());
    }
}
