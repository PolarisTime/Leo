package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.service.PrintRecordItem;
import com.leo.erp.system.printtemplate.service.PrintScriptService;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
@RequestMapping("/print")
public class PrintScriptController {

    private final PrintScriptService printScriptService;
    private final PrintOutputService printOutputService;
    private final ModulePermissionGuard modulePermissionGuard;

    public PrintScriptController(PrintScriptService printScriptService,
                                 PrintOutputService printOutputService,
                                 ModulePermissionGuard modulePermissionGuard) {
        this.printScriptService = printScriptService;
        this.printOutputService = printOutputService;
        this.modulePermissionGuard = modulePermissionGuard;
    }

    /** 统一打印接口：由输出服务封装 PDF 与坐标套打响应。 */
    @PostMapping("/record")
    @PreAuthorize("isAuthenticated()")
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    public ApiResponse<PrintOutput> fromRecord(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody @NotNull PrintRecordRequest payload) {
        String moduleKey = payload.moduleKey();
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        PrintOutput result = printOutputService.generateFromRecord(
                payload.templateId(),
                moduleKey,
                payload.recordId(),
                payload.resolvedPrintOptions()
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/brands")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> brands(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody @NotNull Map<String, Object> payload) {
        String moduleKey = String.valueOf(payload.getOrDefault("moduleKey", ""));
        modulePermissionGuard.requirePermission(principal, moduleKey, "read");
        return ApiResponse.success(printScriptService.listBrands(moduleKey, recordIds(payload.get("recordIds"))));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
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
                .map(this::recordId)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Long> recordId(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(text));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
