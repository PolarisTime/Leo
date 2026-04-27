package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.PageUploadRuleDetail;
import com.leo.erp.attachment.service.UpdatePageUploadRuleCommand;
import com.leo.erp.attachment.service.UploadRuleService;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping({"/general-settings/upload-rule", "/upload-rules/page"})
public class UploadRuleController {

    private final UploadRuleService uploadRuleService;

    public UploadRuleController(UploadRuleService uploadRuleService) {
        this.uploadRuleService = uploadRuleService;
    }

    @GetMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<UploadRuleResponse> detail(@RequestParam(required = false) @Size(max = 64) String moduleKey) {
        return ApiResponse.success(toResponse(uploadRuleService.getPageUploadRule(defaultModuleKey(moduleKey))));
    }

    @PutMapping
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "单号规则", actionType = "编辑上传命名规则")
    public ApiResponse<UploadRuleResponse> update(@RequestParam(required = false) @Size(max = 64) String moduleKey,
                                                  @Valid @RequestBody UploadRuleRequest request) {
        return ApiResponse.success(
                "更新成功",
                toResponse(uploadRuleService.updatePageUploadRule(
                        defaultModuleKey(moduleKey),
                        new UpdatePageUploadRuleCommand(request.renamePattern(), request.status(), request.remark())
                ))
        );
    }

    private UploadRuleResponse toResponse(PageUploadRuleDetail detail) {
        return new UploadRuleResponse(
                detail.id(),
                detail.moduleKey(),
                detail.moduleName(),
                detail.ruleCode(),
                detail.ruleName(),
                detail.renamePattern(),
                detail.status(),
                detail.remark(),
                detail.previewFileName()
        );
    }

    private String defaultModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return "general-settings";
        }
        return moduleKey.trim();
    }
}
