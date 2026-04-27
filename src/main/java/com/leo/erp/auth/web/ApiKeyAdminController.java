package com.leo.erp.auth.web;

import com.leo.erp.auth.service.ApiKeyAdminService;
import com.leo.erp.auth.web.dto.ApiKeyActionOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyRequest;
import com.leo.erp.auth.web.dto.ApiKeyResponse;
import com.leo.erp.auth.web.dto.ApiKeyResourceOptionResponse;
import com.leo.erp.auth.web.dto.ApiKeyUserOptionResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.totp.RequiresTotpVerification;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/auth/api-keys")
public class ApiKeyAdminController {

    private final ApiKeyAdminService service;

    public ApiKeyAdminController(ApiKeyAdminService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(resource = "api-key", action = "read")
    public ApiResponse<PageResponse<ApiKeyResponse>> page(
            @BindPageQuery PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String usageScope) {
        return ApiResponse.success(PageResponse.from(service.page(query, keyword, userId, status, usageScope)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "api-key", action = "read")
    public ApiResponse<ApiKeyResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @GetMapping("/user-options")
    @RequiresPermission(resource = "api-key", action = "read")
    public ApiResponse<List<ApiKeyUserOptionResponse>> userOptions(@RequestParam(required = false) @Size(max = 100) String keyword) {
        return ApiResponse.success(service.listAvailableUsers(keyword));
    }

    @GetMapping("/resource-options")
    @RequiresPermission(resource = "api-key", action = "read")
    public ApiResponse<List<ApiKeyResourceOptionResponse>> resourceOptions() {
        return ApiResponse.success(service.listResourceOptions());
    }

    @GetMapping("/action-options")
    @RequiresPermission(resource = "api-key", action = "read")
    public ApiResponse<List<ApiKeyActionOptionResponse>> actionOptions() {
        return ApiResponse.success(service.listActionOptions());
    }

    @PostMapping
    @RequiresPermission(resource = "api-key", action = "create")
    @RequiresTotpVerification
    @OperationLoggable(moduleName = "API Key 管理", actionType = "生成 API Key", businessNoFields = {"keyName"})
    public ApiResponse<ApiKeyResponse> generate(
            @RequestParam @Positive Long userId,
            @Valid @RequestBody ApiKeyRequest request) {
        return ApiResponse.success("API Key 已生成", service.generate(userId, request));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission(resource = "api-key", action = "update")
    @OperationLoggable(moduleName = "API Key 管理", actionType = "禁用 API Key")
    public ApiResponse<Void> revoke(@PathVariable @Positive Long id) {
        service.revoke(id);
        return ApiResponse.success("已禁用", null);
    }
}
