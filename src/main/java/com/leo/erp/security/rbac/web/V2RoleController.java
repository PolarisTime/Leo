package com.leo.erp.security.rbac.web;

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
import com.leo.erp.security.rbac.service.RoleService;
import com.leo.erp.security.rbac.web.dto.RoleDetailResponse;
import com.leo.erp.security.rbac.web.dto.RolePermissionUpdateRequest;
import com.leo.erp.security.rbac.web.dto.RoleRequest;
import com.leo.erp.security.rbac.web.dto.RoleResponse;
import com.leo.erp.security.rbac.web.dto.RoleStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/roles")
public class V2RoleController {

    private final RoleService roleService;

    public V2RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission(PermissionCodes.ROLES_READ)
    public PageResponse<RoleResponse> page(@BindPageQuery(sortFieldKey = "role") PageQuery query,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return PageResponse.from(roleService.page(query, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.ROLES_READ)
    public RoleDetailResponse detail(@PathVariable Long id) {
        return roleService.detail(id);
    }

    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return V2ResponseSupport.created("/roles", roleService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleResponse updateStatus(@PathVariable Long id, @Valid @RequestBody RoleStatusRequest request) {
        return roleService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return V2ResponseSupport.noContent();
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleDetailResponse replacePermissions(@PathVariable Long id,
                                                 @Valid @RequestBody RolePermissionUpdateRequest request) {
        return roleService.replacePermissions(id, request.permissions());
    }
}
