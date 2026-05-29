package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import jakarta.validation.constraints.NotNull;
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

    /** 传单据 ID，后端返回原始模版 + 数据，由前端渲染。 */
    @PostMapping("/record")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, Object>> fromRecord(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        String templateId = String.valueOf(payload.get("templateId"));
        Long recordId = Long.valueOf(String.valueOf(payload.get("recordId")));
        Map<String, Object> result = printScriptService.generateFromRecord(templateId, moduleKey, recordId);
        return ApiResponse.success(result);
    }
}
