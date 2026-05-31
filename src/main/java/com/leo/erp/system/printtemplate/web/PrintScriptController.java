package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.PrintPdfFormService;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
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
import java.util.Map;

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

    /** 统一打印接口：COORD/HTML 返回模板与数据，PDF_FORM 返回 PDF base64。 */
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
}
