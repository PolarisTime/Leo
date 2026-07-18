package com.leo.erp.system.setup.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping("/setup")
public class InitialSetupController {

    private final InitialSetupCoordinator initialSetupCoordinator;

    public InitialSetupController(InitialSetupCoordinator initialSetupCoordinator) {
        this.initialSetupCoordinator = initialSetupCoordinator;
    }

    @GetMapping("/status")
    public ApiResponse<InitialSetupStatusResponse> status() {
        return ApiResponse.success("获取初始化状态成功", initialSetupCoordinator.status());
    }

    @PostMapping("/admin")
    public ApiResponse<String> configureAdmin(
            @Valid @RequestBody InitialSetupAdminSubmitRequest request) {
        return ApiResponse.success("系统首次初始化完成", initialSetupCoordinator.configureAdmin(request));
    }
}
