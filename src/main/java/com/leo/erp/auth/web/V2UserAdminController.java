package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAdminService;
import com.leo.erp.auth.web.dto.UserAccountCreateRequest;
import com.leo.erp.auth.web.dto.UserAccountResponse;
import com.leo.erp.auth.web.dto.UserAccountUpdateRequest;
import com.leo.erp.auth.web.dto.UserPasswordResetRequest;
import com.leo.erp.auth.web.dto.UserStatusUpdateRequest;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员用户管理 API：账号的增删改查、状态切换与密码重置。 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/users")
public class V2UserAdminController {

    private final UserAdminService userAdminService;

    public V2UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Operation(summary = "分页查询用户账号")
    @GetMapping
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_READ)
    public PageResponse<UserAccountResponse> page(@BindPageQuery(sortFieldKey = "user") PageQuery query,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return PageResponse.from(userAdminService.page(query, keyword, status));
    }

    @Operation(summary = "查询用户账号详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_READ)
    public UserAccountResponse detail(@PathVariable Long id) {
        return userAdminService.detail(id);
    }

    @Operation(summary = "创建用户账号")
    @PostMapping
    @V2Created
    @OperationLoggable(moduleName = "用户账号", actionType = "新增")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_WRITE)
    public ResponseEntity<UserAccountResponse> create(@Valid @RequestBody UserAccountCreateRequest request) {
        return V2ResponseSupport.created("/users", userAdminService.create(request));
    }

    @Operation(summary = "编辑用户账号")
    @PutMapping("/{id}")
    @OperationLoggable(moduleName = "用户账号", actionType = "编辑")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_WRITE)
    public UserAccountResponse update(@PathVariable Long id,
                                      @Valid @RequestBody UserAccountUpdateRequest request) {
        return userAdminService.update(id, request);
    }

    @Operation(summary = "启用或停用用户账号")
    @PatchMapping("/{id}/status")
    @OperationLoggable(moduleName = "用户账号", actionType = "状态变更")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_WRITE)
    public UserAccountResponse updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody UserStatusUpdateRequest request) {
        return userAdminService.updateStatus(id, request.status());
    }

    @Operation(summary = "重置用户密码")
    @PostMapping("/{id}/password-resets")
    @V2NoContent
    @OperationLoggable(moduleName = "用户账号", actionType = "重置密码")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_WRITE)
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody UserPasswordResetRequest request) {
        userAdminService.resetPassword(id, request.newPassword());
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "删除用户账号")
    @DeleteMapping("/{id}")
    @V2NoContent
    @OperationLoggable(moduleName = "用户账号", actionType = "删除")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal SecurityPrincipal principal) {
        userAdminService.delete(id, principal == null ? null : principal.id());
        return V2ResponseSupport.noContent();
    }
}
