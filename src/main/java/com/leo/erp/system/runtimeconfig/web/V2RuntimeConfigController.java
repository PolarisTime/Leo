package com.leo.erp.system.runtimeconfig.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.leo.erp.system.runtimeconfig.web.dto.V2RuntimeConfigResponse;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/runtime-config")
public class V2RuntimeConfigController {

    private final RuntimeConfigService runtimeConfigService;
    private final InitialSetupCoordinator initialSetupCoordinator;

    public V2RuntimeConfigController(RuntimeConfigService runtimeConfigService,
                                     InitialSetupCoordinator initialSetupCoordinator) {
        this.runtimeConfigService = runtimeConfigService;
        this.initialSetupCoordinator = initialSetupCoordinator;
    }

    @GetMapping
    public V2RuntimeConfigResponse getRuntimeConfig() {
        return V2RuntimeConfigResponse.from(
                runtimeConfigService.getRuntimeConfig(),
                initialSetupCoordinator.status()
        );
    }
}
