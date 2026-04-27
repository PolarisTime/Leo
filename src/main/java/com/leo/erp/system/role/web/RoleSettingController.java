package com.leo.erp.system.role.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.role.service.RoleSettingService;
import com.leo.erp.system.role.web.dto.RolePermissionItem;
import com.leo.erp.system.role.web.dto.RoleSettingRequest;
import com.leo.erp.system.role.web.dto.RoleSettingResponse;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
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
@RequestMapping("/role-settings")
public class RoleSettingController {

    private final RoleSettingService roleSettingService;

    public RoleSettingController(RoleSettingService roleSettingService) {
        this.roleSettingService = roleSettingService;
    }

    @GetMapping
    @RequiresPermission(resource = "role", action = "read")
    public ApiResponse<PageResponse<RoleSettingResponse>> page(
            @BindPageQuery(sortFieldKey = "role-settings") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(roleSettingService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "role", action = "read")
    public ApiResponse<RoleSettingResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(roleSettingService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "role", action = "create")
    @OperationLoggable(moduleName = "角色权限配置", actionType = "新增", businessNoFields = {"roleCode"})
    public ApiResponse<RoleSettingResponse> create(@Valid @RequestBody RoleSettingRequest request) {
        return ApiResponse.success("创建成功", roleSettingService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "role", action = "update")
    @OperationLoggable(moduleName = "角色权限配置", actionType = "编辑", businessNoFields = {"roleCode"})
    public ApiResponse<RoleSettingResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody RoleSettingRequest request) {
        return ApiResponse.success("更新成功", roleSettingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "role", action = "delete")
    @OperationLoggable(moduleName = "角色权限配置", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        roleSettingService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/{id}/permissions")
    @RequiresPermission(resource = "role", action = "manage_permissions")
    public ApiResponse<List<RolePermissionItem>> getRolePermissions(@PathVariable @Positive Long id) {
        return ApiResponse.success(roleSettingService.getRolePermissions(id));
    }

    @GetMapping("/permission-options")
    @RequiresPermission(resource = "role", action = "manage_permissions")
    public ApiResponse<List<MenuTreeResponse>> listPermissionOptions() {
        return ApiResponse.success(roleSettingService.listPermissionOptions());
    }

    @PutMapping("/{id}/permissions")
    @RequiresPermission(resource = "role", action = "manage_permissions")
    @OperationLoggable(moduleName = "角色权限配置", actionType = "编辑权限")
    public ApiResponse<Void> saveRolePermissions(@PathVariable @Positive Long id,
                                                 @Valid @RequestBody List<@Valid RolePermissionItem> permissions) {
        roleSettingService.saveRolePermissions(id, permissions);
        return ApiResponse.success("权限保存成功", null);
    }
}
