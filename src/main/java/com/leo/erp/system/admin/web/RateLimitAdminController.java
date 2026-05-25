package com.leo.erp.system.admin.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.admin.service.RateLimitAdminService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/admin/rate-limit")
public class RateLimitAdminController {

    private final RateLimitAdminService service;

    public RateLimitAdminController(RateLimitAdminService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<List<Map<String, Object>>> rules() {
        return ApiResponse.success(service.listRules());
    }

    @PutMapping("/rules/{id}")
    @RequiresPermission(resource = "general-setting", action = "update")
    public ApiResponse<Void> updateRule(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        service.updateRule(id, body);
        return ApiResponse.success(null);
    }
}
