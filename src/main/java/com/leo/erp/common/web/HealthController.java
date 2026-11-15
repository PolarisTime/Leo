package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.web.dto.HealthResponse;
import com.leo.erp.common.web.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "健康检查", description = "返回应用健康快照；全部依赖正常返回 200，任一依赖异常返回 503。@PublicAccess 免认证。")
    @PublicAccess
    @GetMapping(ApiVersion.PREFIX + "/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse health = healthService.health();
        return ResponseEntity.status(statusOf(health)).body(health);
    }

    private HttpStatus statusOf(HealthResponse health) {
        return healthService.isUp(health) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    }
}
