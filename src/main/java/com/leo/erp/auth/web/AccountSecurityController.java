package com.leo.erp.auth.web;

import com.leo.erp.auth.service.AccountSecurityService;
import com.leo.erp.auth.web.dto.ChangeOwnPasswordRequest;
import com.leo.erp.auth.web.dto.CurrentUserSecurityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.security.totp.RequiresTotpVerification;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/security")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;

    public AccountSecurityController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @GetMapping
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<CurrentUserSecurityResponse> status(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success(accountSecurityService.getStatus(principal.id()));
    }

    @PostMapping("/password")
    @RequiresPermission(authenticatedOnly = true)
    @OperationLoggable(moduleName = "个人设置", actionType = "修改密码")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        accountSecurityService.changePassword(principal.id(), request);
        return ApiResponse.success("密码修改成功", null);
    }

    @PostMapping("/2fa/setup")
    @RequiresPermission(authenticatedOnly = true)
    @OperationLoggable(moduleName = "个人设置", actionType = "生成2FA密钥")
    public ApiResponse<TotpSetupResponse> setup2fa(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success("密钥生成成功", accountSecurityService.setup2fa(principal.id()));
    }

    @PostMapping("/2fa/enable")
    @RequiresPermission(authenticatedOnly = true)
    @OperationLoggable(moduleName = "个人设置", actionType = "启用2FA")
    public ApiResponse<CurrentUserSecurityResponse> enable2fa(@AuthenticationPrincipal SecurityPrincipal principal,
                                                              @Valid @RequestBody TotpEnableRequest request) {
        return ApiResponse.success("2FA已启用", accountSecurityService.enable2fa(principal.id(), request));
    }

    @PostMapping("/2fa/disable")
    @RequiresPermission(authenticatedOnly = true)
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "个人设置", actionType = "禁用2FA")
    public ApiResponse<CurrentUserSecurityResponse> disable2fa(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success("2FA已禁用", accountSecurityService.disable2fa(principal.id()));
    }
}
