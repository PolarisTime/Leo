package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
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
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
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
                PrintOptions.from(payload.get("printOptions"))
        );
        if ("PDF_FORM".equals(String.valueOf(result.getOrDefault("templateType", "")))) {
            byte[] pdf = printPdfFormService.generateFromPayload(result);
            Map<String, Object> pdfResult = new HashMap<>();
            pdfResult.put("templateName", result.get("templateName"));
            pdfResult.put("templateType", "PDF_FORM");
            pdfResult.put("contentType", MediaType.APPLICATION_PDF_VALUE);
            pdfResult.put("fileName", "print.pdf");
            pdfResult.put("pdfBase64", Base64.getEncoder().encodeToString(pdf));
            pdfResult.put("businessNo", result.get("businessNo"));
            pdfResult.put("recordId", result.get("recordId"));
            pdfResult.put("moduleKey", result.get("moduleKey"));
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

    private List<Long> recordIds(Object rawRecordIds) {
        if (!(rawRecordIds instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> Long.valueOf(String.valueOf(value)))
                .toList();
    }
}
