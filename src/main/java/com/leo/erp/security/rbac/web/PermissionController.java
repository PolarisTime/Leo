package com.leo.erp.security.rbac.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.rbac.service.PermissionCatalogService;
import com.leo.erp.security.rbac.web.dto.PermissionResponse;
import org.springframework.validation.annotation.Validated;
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

    @GetMapping
    @RequirePermission(PermissionCodes.PERMISSIONS_READ)
    public List<PermissionResponse> catalog() {
        return permissionCatalogService.listCatalog();
    }
}
