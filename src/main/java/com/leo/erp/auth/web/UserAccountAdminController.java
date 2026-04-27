package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAccountAdminService;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminRequest;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/user-accounts")
public class UserAccountAdminController {

    private final UserAccountAdminService userAccountAdminService;

    public UserAccountAdminController(UserAccountAdminService userAccountAdminService) {
        this.userAccountAdminService = userAccountAdminService;
    }

    @GetMapping
    @RequiresPermission(resource = "user-account", action = "read")
    public ApiResponse<PageResponse<UserAccountAdminResponse>> page(
            @BindPageQuery(sortFieldKey = "user-accounts") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(userAccountAdminService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "user-account", action = "read")
    public ApiResponse<UserAccountAdminResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(userAccountAdminService.detail(id));
    }

    @GetMapping("/login-name-availability")
    @RequiresPermission(resource = "user-account", action = "read")
    public ApiResponse<LoginNameAvailabilityResponse> checkLoginNameAvailability(
            @RequestParam String loginName,
            @RequestParam(required = false) @Positive Long excludeUserId
    ) {
        return ApiResponse.success(userAccountAdminService.checkLoginNameAvailability(loginName, excludeUserId));
    }

    @PostMapping
    @RequiresPermission(resource = "user-account", action = "create")
    @OperationLoggable(moduleName = "用户账户", actionType = "新增", businessNoFields = {"loginName"})
    public ApiResponse<UserAccountCreateResponse> create(@Valid @RequestBody UserAccountAdminRequest request) {
        return ApiResponse.success("创建成功", userAccountAdminService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "user-account", action = "update")
    @OperationLoggable(moduleName = "用户账户", actionType = "编辑", businessNoFields = {"loginName"})
    public ApiResponse<UserAccountAdminResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody UserAccountAdminRequest request) {
        return ApiResponse.success("更新成功", userAccountAdminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "user-account", action = "delete")
    @OperationLoggable(moduleName = "用户账户", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        userAccountAdminService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    // --- 2FA 管理 ---

    @PostMapping("/{id}/2fa/setup")
    @RequiresPermission(resource = "user-account", action = "update")
    @OperationLoggable(moduleName = "用户账户", actionType = "生成2FA密钥")
    public ApiResponse<TotpSetupResponse> setup2fa(@PathVariable @Positive Long id) {
        return ApiResponse.success("密钥生成成功", userAccountAdminService.setup2fa(id));
    }

    @PostMapping("/{id}/2fa/enable")
    @RequiresPermission(resource = "user-account", action = "update")
    @OperationLoggable(moduleName = "用户账户", actionType = "启用2FA")
    public ApiResponse<UserAccountAdminResponse> enable2fa(@PathVariable @Positive Long id, @Valid @RequestBody TotpEnableRequest request) {
        return ApiResponse.success("2FA已启用", userAccountAdminService.enable2fa(id, request));
    }

    @PostMapping("/{id}/2fa/disable")
    @RequiresPermission(resource = "user-account", action = "update")
    @OperationLoggable(moduleName = "用户账户", actionType = "禁用2FA")
    public ApiResponse<UserAccountAdminResponse> disable2fa(@PathVariable @Positive Long id) {
        return ApiResponse.success("2FA已禁用", userAccountAdminService.disable2fa(id));
    }
}
