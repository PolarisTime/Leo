package com.leo.erp.system.permission.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.permission.service.PermissionEntryService;
import com.leo.erp.system.permission.web.dto.CatalogActionResponse;
import com.leo.erp.system.permission.web.dto.CatalogEntryResponse;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/permission-management")
public class PermissionEntryController {

    private final PermissionEntryService permissionEntryService;

    public PermissionEntryController(PermissionEntryService permissionEntryService) {
        this.permissionEntryService = permissionEntryService;
    }

    @GetMapping("/catalog")
    @RequiresPermission(resource = "permission", action = "read")
    public ApiResponse<List<CatalogEntryResponse>> catalog() {
        List<CatalogEntryResponse> entries = ResourcePermissionCatalog.entries().stream()
                .map(entry -> new CatalogEntryResponse(
                        entry.code(),
                        entry.title(),
                        entry.group(),
                        entry.businessResource(),
                        entry.menuCodes(),
                        entry.pathPrefixes(),
                        entry.actions().stream()
                                .map(action -> new CatalogActionResponse(action.code(), action.title()))
                                .toList()
                ))
                .toList();
        return ApiResponse.success(entries);
    }

    @GetMapping
    @RequiresPermission(resource = "permission", action = "read")
    public ApiResponse<PageResponse<PermissionEntryResponse>> page(
            @BindPageQuery(sortFieldKey = "permission-management") PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(PageResponse.from(permissionEntryService.page(query, keyword)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "permission", action = "read")
    public ApiResponse<PermissionEntryResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(permissionEntryService.detail(id));
    }
}
