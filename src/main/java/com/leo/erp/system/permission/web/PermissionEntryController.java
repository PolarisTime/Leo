package com.leo.erp.system.permission.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.permission.service.PermissionEntryService;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permission-management")
public class PermissionEntryController {

    private final PermissionEntryService permissionEntryService;

    public PermissionEntryController(PermissionEntryService permissionEntryService) {
        this.permissionEntryService = permissionEntryService;
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
