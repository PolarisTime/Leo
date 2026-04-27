package com.leo.erp.system.norule.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.norule.service.GeneralSettingQueryService;
import com.leo.erp.system.norule.service.NoRuleService;
import com.leo.erp.system.norule.service.NoRuleSequenceService;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.norule.web.dto.GeneralSettingResponse;
import com.leo.erp.system.norule.web.dto.NoRuleGenerateResponse;
import com.leo.erp.system.norule.web.dto.NoRuleRequest;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/general-settings")
public class NoRuleController {

    private final NoRuleService noRuleService;
    private final GeneralSettingQueryService generalSettingQueryService;
    private final NoRuleSequenceService noRuleSequenceService;
    private final ModulePermissionGuard modulePermissionGuard;

    public NoRuleController(NoRuleService noRuleService,
                            GeneralSettingQueryService generalSettingQueryService,
                            NoRuleSequenceService noRuleSequenceService,
                            ModulePermissionGuard modulePermissionGuard) {
        this.noRuleService = noRuleService;
        this.generalSettingQueryService = generalSettingQueryService;
        this.noRuleSequenceService = noRuleSequenceService;
        this.modulePermissionGuard = modulePermissionGuard;
    }

    @GetMapping
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<PageResponse<GeneralSettingResponse>> page(
            @BindPageQuery(sortFieldKey = "general-settings") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(PageResponse.from(generalSettingQueryService.page(query, keyword, status)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<NoRuleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(noRuleService.detail(id));
    }

    @PostMapping("/number-rules/next")
    @RequiresPermission(authenticatedOnly = true)
    public ApiResponse<NoRuleGenerateResponse> nextNumber(@AuthenticationPrincipal SecurityPrincipal principal,
                                                          @RequestParam String moduleKey) {
        String normalizedModuleKey = modulePermissionGuard.requirePermission(principal, moduleKey, "create");
        return ApiResponse.success(new NoRuleGenerateResponse(
                normalizedModuleKey,
                noRuleSequenceService.nextValueByModuleKey(normalizedModuleKey)
        ));
    }

    @PostMapping
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "通用设置", actionType = "新增", businessNoFields = {"settingCode"})
    public ApiResponse<NoRuleResponse> create(@Valid @RequestBody NoRuleRequest request) {
        return ApiResponse.success("创建成功", noRuleService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "通用设置", actionType = "编辑", businessNoFields = {"settingCode"})
    public ApiResponse<NoRuleResponse> update(@PathVariable Long id, @Valid @RequestBody NoRuleRequest request) {
        return ApiResponse.success("更新成功", noRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "通用设置", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noRuleService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
