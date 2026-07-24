package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAccountPreferenceService;
import com.leo.erp.auth.service.UserAccountService;
import com.leo.erp.auth.web.dto.CurrentAccountResponse;
import com.leo.erp.auth.web.dto.CurrentAccountUpdateRequest;
import com.leo.erp.auth.web.dto.PasswordChangeRequest;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前唯一账号接口；资源归属由认证主体确定，不接受外部账号 ID。 */
@RestController
@Validated
@RequestMapping("/account")
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final UserAccountPreferenceService userAccountPreferenceService;

    public UserAccountController(UserAccountService userAccountService,
                                 UserAccountPreferenceService userAccountPreferenceService) {
        this.userAccountService = userAccountService;
        this.userAccountPreferenceService = userAccountPreferenceService;
    }

    @GetMapping
    public ApiResponse<CurrentAccountResponse> current(
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success(userAccountService.current(currentUserId(principal)));
    }

    @PutMapping
    @OperationLoggable(moduleName = "个人账号", actionType = "编辑")
    public ApiResponse<CurrentAccountResponse> update(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CurrentAccountUpdateRequest request) {
        return ApiResponse.success("保存成功",
                userAccountService.updateCurrent(currentUserId(principal), request));
    }

    @PutMapping("/password")
    @OperationLoggable(moduleName = "身份认证", actionType = "修改密码")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        userAccountService.changePassword(currentUserId(principal), request);
        return ApiResponse.success("密码已更新，请重新登录");
    }

    @GetMapping("/preferences")
    public ApiResponse<UserAccountPreferencesPayload> preferences(
            @AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success(userAccountPreferenceService.getPreferences(currentUserId(principal)));
    }

    @PutMapping("/preferences")
    public ApiResponse<UserAccountPreferencesPayload> savePreferences(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody UserAccountPreferencesPayload request) {
        return ApiResponse.success("保存成功",
                userAccountPreferenceService.savePreferences(currentUserId(principal), request));
    }

    private Long currentUserId(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw new IllegalStateException("认证主体不可用");
        }
        return principal.id();
    }
}
