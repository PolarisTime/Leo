package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintOptions;
import com.leo.erp.system.printtemplate.service.PrintPdfFormService;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.service.PrintScriptService.PrintRecordItem;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/print")
public class PrintScriptController {

    private final PrintScriptService printScriptService;
    private final PrintPdfFormService printPdfFormService;
    private final ModulePermissionGuard modulePermissionGuard;

    public PrintScriptController(PrintScriptService printScriptService,
                                 PrintPdfFormService printPdfFormService,
                                 ModulePermissionGuard modulePermissionGuard) {
        this.printScriptService = printScriptService;
        this.printPdfFormService = printPdfFormService;
        this.modulePermissionGuard = modulePermissionGuard;
    }

    /** 统一打印接口：COORD 返回套打脚本与数据，PDF_FORM 返回 PDF base64。 */
    @PostMapping("/record")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, Object>> fromRecord(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        String templateId = String.valueOf(payload.get("templateId"));
        Long recordId = Long.valueOf(String.valueOf(payload.get("recordId")));
        Map<String, Object> result = printScriptService.generateFromRecord(
                templateId,
                moduleKey,
                recordId,
                printOptions(payload.get("printOptions"))
        );
        if ("PDF_FORM".equals(String.valueOf(result.getOrDefault("templateType", "")))) {
            byte[] pdf = printPdfFormService.generateFromPayload(result);
            Map<String, Object> pdfResult = new HashMap<>();
            pdfResult.put("templateName", result.get("templateName"));
            pdfResult.put("templateType", "PDF_FORM");
            pdfResult.put("contentType", MediaType.APPLICATION_PDF_VALUE);
            pdfResult.put("fileName", "print.pdf");
            pdfResult.put("pdfBase64", Base64.getEncoder().encodeToString(pdf));
            result = pdfResult;
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/brands")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<List<String>> brands(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        return ApiResponse.success(printScriptService.listBrands(moduleKey, recordIds(payload.get("recordIds"))));
    }

    @PostMapping("/items")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<List<PrintRecordItem>> items(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        return ApiResponse.success(printScriptService.listPrintItems(moduleKey, recordIds(payload.get("recordIds"))));
    }

    private PrintOptions printOptions(Object rawOptions) {
        if (!(rawOptions instanceof Map<?, ?> options)) {
            return PrintOptions.defaults();
        }
        Object hideUnitPrice = options.get("hideUnitPrice");
        Object brandOverride = options.get("brandOverride");
        Map<String, String> brandOverrides = brandOverrides(options.get("brandOverrides"));
        Map<String, String> brandOverridesByItemId = brandOverrides(options.get("brandOverridesByItemId"));
        return new PrintOptions(
                Boolean.TRUE.equals(hideUnitPrice),
                brandOverride instanceof String value ? value.trim() : "",
                brandOverrides,
                brandOverridesByItemId
        );
    }

    private List<Long> recordIds(Object rawRecordIds) {
        if (!(rawRecordIds instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> Long.valueOf(String.valueOf(value)))
                .toList();
    }

    private Map<String, String> brandOverrides(Object rawBrandOverrides) {
        if (!(rawBrandOverrides instanceof Map<?, ?> values)) {
            return Map.of();
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String)
                .map(entry -> Map.entry(
                        String.valueOf(entry.getKey()).trim(),
                        String.valueOf(entry.getValue()).trim()
                ))
                .filter(entry -> !entry.getKey().isBlank() && !entry.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, ignored) -> left));
    }
}
