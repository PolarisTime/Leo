package com.leo.erp.security.rbac.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.rbac.service.PermissionCatalogService;
import com.leo.erp.security.rbac.web.dto.PermissionResponse;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping(ApiVersion.PREFIX + "/permissions")
public class PermissionController {

    private final PermissionCatalogService permissionCatalogService;

    public PermissionController(PermissionCatalogService permissionCatalogService) {
        this.permissionCatalogService = permissionCatalogService;
    }

    @Operation(summary = "查询权限目录", description = "返回系统内置权限码目录（按资源分组），供角色授权界面渲染；不分页。需 PERMISSIONS_READ 权限。")
    @GetMapping
    @RequirePermission(PermissionCodes.PERMISSIONS_READ)
    public List<PermissionResponse> catalog() {
        return permissionCatalogService.listCatalog();
    }
}
