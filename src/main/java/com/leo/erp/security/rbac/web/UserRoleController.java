package com.leo.erp.security.rbac.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.rbac.service.UserRoleService;
import com.leo.erp.security.rbac.web.dto.UserRolesResponse;
import com.leo.erp.security.rbac.web.dto.UserRolesUpdateRequest;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/users")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Operation(summary = "查询用户已分配角色", description = "按用户雪花 ID 返回其角色集合；不存在用户返回 404。需 USER_ACCOUNTS_READ 权限。")
    @GetMapping("/{id}/roles")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_READ)
    public UserRolesResponse roles(@PathVariable @SnowflakeId Long id) {
        return userRoleService.rolesOf(id);
    }

    @Operation(summary = "整体替换用户角色", description = "PUT 全量替换用户角色集合；禁止修改自己的角色（403），成功返回 200 与更新后的角色列表，重复提交结果一致（幂等）。需 USER_ACCOUNTS_UPDATE 权限。")
    @PutMapping("/{id}/roles")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_UPDATE)
    public UserRolesResponse replaceRoles(@PathVariable @SnowflakeId Long id,
                                          @AuthenticationPrincipal SecurityPrincipal principal,
                                          @Valid @RequestBody UserRolesUpdateRequest request) {
        // 防止权限自提升: 任何用户(含管理员)不得修改自己的角色分配。
        if (principal != null && id != null && id.equals(principal.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能修改自己的角色，请联系其他管理员");
        }
        return userRoleService.replaceRoles(id, request.roleIds());
    }
}
