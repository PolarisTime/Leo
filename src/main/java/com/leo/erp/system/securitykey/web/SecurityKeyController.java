package com.leo.erp.system.securitykey.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.totp.RequiresTotpVerification;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyOverviewResponse;
import com.leo.erp.system.securitykey.web.dto.SecurityKeyRotateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/security-keys")
public class SecurityKeyController {

    private final SecurityKeyService securityKeyService;

    public SecurityKeyController(SecurityKeyService securityKeyService) {
        this.securityKeyService = securityKeyService;
    }

    @GetMapping
    @RequiresPermission(resource = "security-key", action = "read")
    public ApiResponse<SecurityKeyOverviewResponse> overview() {
        return ApiResponse.success(securityKeyService.getOverview());
    }

    @PostMapping("/jwt/rotate")
    @RequiresPermission(resource = "security-key", action = "update")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "安全密钥管理", actionType = "轮转JWT主密钥")
    public ApiResponse<SecurityKeyRotateResponse> rotateJwt() {
        return ApiResponse.success("JWT 主密钥轮转成功", securityKeyService.rotateJwtMasterKey());
    }

    @PostMapping("/totp/rotate")
    @RequiresPermission(resource = "security-key", action = "update")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "安全密钥管理", actionType = "轮转2FA主密钥")
    public ApiResponse<SecurityKeyRotateResponse> rotateTotp() {
        return ApiResponse.success("2FA 主密钥轮转成功", securityKeyService.rotateTotpMasterKey());
    }
}
