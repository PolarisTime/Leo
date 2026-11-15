package com.leo.erp.security.rbac.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.Created;
import com.leo.erp.common.api.NoContent;
import com.leo.erp.common.api.ResponseSupport;
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
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping(ApiVersion.PREFIX + "/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "分页查询角色", description = "按关键字与状态过滤，分页返回（PageResponse），默认按 role 稳定排序。需 ROLES_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.ROLES_READ)
    public PageResponse<RoleResponse> page(@BindPageQuery(sortFieldKey = "role") PageQuery query,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return PageResponse.from(roleService.page(query, keyword, status));
    }

    @Operation(summary = "查询角色详情", description = "按雪花 ID 查询角色及其权限清单（路径 id 以字符串传递）；不存在返回 404。需 ROLES_READ 权限。")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.ROLES_READ)
    public RoleDetailResponse detail(@PathVariable @SnowflakeId Long id) {
        return roleService.detail(id);
    }

    @Operation(summary = "创建角色", description = "成功返回 201 与 Location /roles/{id}，响应体为新建角色（id 为雪花 ID 字符串）。重复提交需 Idempotency-Key（类级强制）。需 ROLES_WRITE 权限。")
    @PostMapping
    @Created
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseSupport.created("/roles", roleService.create(request));
    }

    @Operation(summary = "更新角色", description = "PUT 整体替换角色基本信息，成功返回 200；路径 id 为雪花 ID 字符串。重复提交需 Idempotency-Key（类级强制）。需 ROLES_WRITE 权限。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleResponse update(@PathVariable @SnowflakeId Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @Operation(summary = "变更角色状态", description = "PATCH 仅更新启用/停用状态字段，成功返回 200 与更新后的角色；重复提交结果一致（幂等）。需 ROLES_WRITE 权限。")
    @PatchMapping("/{id}/status")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleResponse updateStatus(@PathVariable @SnowflakeId Long id, @Valid @RequestBody RoleStatusRequest request) {
        return roleService.updateStatus(id, request.status());
    }

    @Operation(summary = "删除角色", description = "幂等删除，成功返回 204 No Content；仍被用户引用的角色由服务层校验并返回业务错误。需 ROLES_WRITE 权限。")
    @DeleteMapping("/{id}")
    @NoContent
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public ResponseEntity<Void> delete(@PathVariable @SnowflakeId Long id) {
        roleService.delete(id);
        return ResponseSupport.noContent();
    }

    @Operation(summary = "整体替换角色权限", description = "PUT 全量替换角色的权限集合（未列出的权限被移除），成功返回 200 与更新后的角色详情；重复提交结果一致（幂等）。需 ROLES_WRITE 权限。")
    @PutMapping("/{id}/permissions")
    @RequirePermission(PermissionCodes.ROLES_WRITE)
    public RoleDetailResponse replacePermissions(@PathVariable @SnowflakeId Long id,
                                                 @Valid @RequestBody RolePermissionUpdateRequest request) {
        return roleService.replacePermissions(id, request.permissions());
    }
}
