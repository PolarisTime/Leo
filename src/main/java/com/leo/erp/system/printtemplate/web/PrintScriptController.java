package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import jakarta.validation.constraints.NotBlank;
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

    public PrintScriptController(PrintScriptService printScriptService) {
        this.printScriptService = printScriptService;
    }

    @PostMapping("/generate")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, String>> generate(
            @RequestBody @NotBlank Map<String, Object> payload) {
        String templateId = String.valueOf(payload.get("templateId"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.getOrDefault("data", Map.of());
        String script = printScriptService.generate(templateId, data);
        return ApiResponse.success(Map.of("script", script));
    }
}
