package com.leo.erp.auth.web;

import com.leo.erp.auth.service.RefreshTokenAdminService;
import com.leo.erp.auth.web.dto.RefreshTokenAdminResponse;
import com.leo.erp.auth.web.dto.RefreshTokenSessionSummaryResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/auth/refresh-tokens")
public class RefreshTokenAdminController {

    private final RefreshTokenAdminService service;

    public RefreshTokenAdminController(RefreshTokenAdminService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(resource = "session", action = "read")
    public ApiResponse<PageResponse<RefreshTokenAdminResponse>> page(
            @BindPageQuery PageQuery query,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(PageResponse.from(service.pageWithUserInfo(query, keyword)));
    }

    @GetMapping("/summary")
    @RequiresPermission(resource = "session", action = "read")
    public ApiResponse<RefreshTokenSessionSummaryResponse> summary() {
        return ApiResponse.success(service.summary());
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission(resource = "session", action = "update")
    public ApiResponse<Void> revoke(@PathVariable @Positive Long id) {
        service.revoke(id);
        return ApiResponse.success("已禁用");
    }

    @PostMapping("/revoke-all")
    @RequiresPermission(resource = "session", action = "update")
    public ApiResponse<Integer> revokeAll() {
        int count = service.revokeAll();
        return ApiResponse.success("已禁用 " + count + " 个令牌", count);
    }
}
