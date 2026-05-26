package com.leo.erp.system.printtemplate.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.service.ClodopScriptGenerator;
import com.leo.erp.system.printtemplate.service.JsonPrintTemplate;
import com.leo.erp.system.printtemplate.service.PdfPrintGenerator;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/print")
public class PrintScriptController {

    private final PrintScriptService printScriptService;
    private final ClodopScriptGenerator clodopScriptGenerator;
    private final PdfPrintGenerator pdfPrintGenerator;
    private final ModulePermissionGuard modulePermissionGuard;
    private final ObjectMapper objectMapper;

    public PrintScriptController(PrintScriptService printScriptService,
                                  ClodopScriptGenerator clodopScriptGenerator,
                                  PdfPrintGenerator pdfPrintGenerator,
                                  ModulePermissionGuard modulePermissionGuard,
                                  ObjectMapper objectMapper) {
        this.printScriptService = printScriptService;
        this.clodopScriptGenerator = clodopScriptGenerator;
        this.pdfPrintGenerator = pdfPrintGenerator;
        this.modulePermissionGuard = modulePermissionGuard;
        this.objectMapper = objectMapper;
    }

    /** 传单据 ID，后端从 DB 加载数据生成脚本（防篡改） */
    @PostMapping("/record")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, String>> fromRecord(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        String templateId = String.valueOf(payload.get("templateId"));
        Long recordId = Long.valueOf(String.valueOf(payload.get("recordId")));
        String script = printScriptService.generateFromRecord(templateId, moduleKey, recordId);
        return ApiResponse.success(Map.of("script", script));
    }

    @PostMapping("/generate")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, String>> generate(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String templateId = String.valueOf(payload.get("templateId"));
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.getOrDefault("data", Map.of());
        String script = printScriptService.generate(templateId, data);
        return ApiResponse.success(Map.of("script", script));
    }

    /** JSON 模板 → CLODOP 套打脚本 */
    @PostMapping("/clodop")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<Map<String, String>> clodop(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        JsonPrintTemplate template = parseTemplate(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.getOrDefault("items", List.of());
        String title = String.valueOf(payload.getOrDefault("title", ""));
        String script = clodopScriptGenerator.generate(template, data, items, title);
        return ApiResponse.success(Map.of("script", script));
    }

    /** JSON 模板 → iText PDF 文件 */
    @PostMapping("/pdf")
    @RequiresPermission(authenticatedOnly = true)
    public ResponseEntity<byte[]> pdf(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) throws IOException {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        JsonPrintTemplate template = parseTemplate(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.getOrDefault("items", List.of());
        String title = String.valueOf(payload.getOrDefault("title", ""));
        byte[] pdf = pdfPrintGenerator.generate(template, data, items, title);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeFileName(title) + ".pdf\"")
                .body(pdf);
    }

    private JsonPrintTemplate parseTemplate(Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tpl = (Map<String, Object>) payload.get("template");
            if (tpl == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少 template 定义");
            return objectMapper.convertValue(tpl, JsonPrintTemplate.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板格式错误: " + e.getMessage());
        }
    }

    private String safeFileName(String title) {
        return title.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
