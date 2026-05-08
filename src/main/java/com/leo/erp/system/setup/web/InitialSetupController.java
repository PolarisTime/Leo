package com.leo.erp.system.setup.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.setup.service.InitialSetupService;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupTotpSetupRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@RequestMapping("/setup")
public class InitialSetupController {

    private final InitialSetupService initialSetupService;
    private final SystemSwitchService systemSwitchService;

    public InitialSetupController(InitialSetupService initialSetupService,
                                  SystemSwitchService systemSwitchService) {
        this.initialSetupService = initialSetupService;
        this.systemSwitchService = systemSwitchService;
    }

    @GetMapping("/status")
    public ApiResponse<InitialSetupStatusResponse> status() {
        return ApiResponse.success("获取初始化状态成功", initialSetupService.status());
    }

    @PostMapping("/initialize")
    public ApiResponse<InitialSetupSubmitResponse> initialize(@Valid @RequestBody InitialSetupSubmitRequest request) {
        assertOobeNotCompleted();
        return ApiResponse.success("系统首次初始化完成", initialSetupService.initialize(request));
    }

    @PostMapping("/admin/2fa/setup")
    public ApiResponse<TotpSetupResponse> setupAdminTotp(@Valid @RequestBody InitialSetupTotpSetupRequest request) {
        assertOobeNotCompleted();
        return ApiResponse.success("管理员 2FA 已生成", initialSetupService.setupAdminTotp(request));
    }

    @PostMapping("/admin")
    public ApiResponse<InitialSetupSubmitResponse> configureAdmin(
            @Valid @RequestBody InitialSetupAdminSubmitRequest request) {
        assertOobeNotCompleted();
        return ApiResponse.success("管理员账号初始化完成", initialSetupService.configureAdmin(request));
    }

    @PostMapping("/company")
    public ApiResponse<InitialSetupSubmitResponse> configureCompany(
            @Valid @RequestBody InitialSetupCompanyRequest request) {
        assertOobeNotCompleted();
        return ApiResponse.success("公司主体初始化完成", initialSetupService.configureCompany(request));
    }

    private void assertOobeNotCompleted() {
        if (systemSwitchService != null && systemSwitchService.isOobeCompleted()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已完成初始化，该接口已禁用");
        }
    }
}
