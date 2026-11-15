package com.leo.erp.system.runtimeconfig.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.leo.erp.system.runtimeconfig.web.dto.RuntimeConfigSnapshotResponse;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.PREFIX + "/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigService runtimeConfigService;
    private final InitialSetupCoordinator initialSetupCoordinator;

    public RuntimeConfigController(RuntimeConfigService runtimeConfigService,
                                     InitialSetupCoordinator initialSetupCoordinator) {
        this.runtimeConfigService = runtimeConfigService;
        this.initialSetupCoordinator = initialSetupCoordinator;
    }

    @Operation(summary = "查询运行时配置快照", description = "返回前端启动所需的运行时配置（菜单与功能开关等）及初始化状态；@PublicAccess 免认证，供登录页与初始化向导使用。")
    @GetMapping
    public RuntimeConfigSnapshotResponse getRuntimeConfig() {
        return RuntimeConfigSnapshotResponse.from(
                runtimeConfigService.getRuntimeConfig(),
                initialSetupCoordinator.status()
        );
    }
}
