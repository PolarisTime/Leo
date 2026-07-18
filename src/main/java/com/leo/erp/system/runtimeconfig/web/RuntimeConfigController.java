package com.leo.erp.system.runtimeconfig.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigService runtimeConfigService;

    public RuntimeConfigController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping
    public ApiResponse<RuntimeConfigResponse> getRuntimeConfig() {
        return ApiResponse.success(runtimeConfigService.getRuntimeConfig());
    }
}
