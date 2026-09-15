package com.leo.erp.security.rbac.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.rbac.service.UserRoleService;
import com.leo.erp.security.rbac.web.dto.UserRolesResponse;
import com.leo.erp.security.rbac.web.dto.UserRolesUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/users")
public class V2UserRoleController {

    private final UserRoleService userRoleService;

    public V2UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/{id}/roles")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_READ)
    public UserRolesResponse roles(@PathVariable Long id) {
        return userRoleService.rolesOf(id);
    }

    @PutMapping("/{id}/roles")
    @RequirePermission(PermissionCodes.USER_ACCOUNTS_UPDATE)
    public UserRolesResponse replaceRoles(@PathVariable Long id,
                                          @Valid @RequestBody UserRolesUpdateRequest request) {
        return userRoleService.replaceRoles(id, request.roleIds());
    }
}
