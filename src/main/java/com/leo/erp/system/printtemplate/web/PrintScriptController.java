package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Validated
@RequestMapping("/print")
public class PrintScriptController {

    private final PrintScriptService printScriptService;
    private final ModulePermissionGuard modulePermissionGuard;

    public PrintScriptController(PrintScriptService printScriptService,
                                  ModulePermissionGuard modulePermissionGuard) {
        this.printScriptService = printScriptService;
        this.modulePermissionGuard = modulePermissionGuard;
    }

    @PostMapping("/generate")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, String>> generate(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotBlank Map<String, Object> payload) {
        String templateId = String.valueOf(payload.get("templateId"));
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        // 抽象打印权限: 用户需要对应模块的 read 权限
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.getOrDefault("data", Map.of());
        String script = printScriptService.generate(templateId, data);
        return ApiResponse.success(Map.of("script", script));
    }
}
