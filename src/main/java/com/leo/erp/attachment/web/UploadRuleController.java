package com.leo.erp.attachment.web;

import com.leo.erp.attachment.mapper.UploadRuleWebMapper;
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
@RequestMapping("/general-setting/upload-rule")
public class UploadRuleController {

    private final UploadRuleService uploadRuleService;
    private final UploadRuleWebMapper uploadRuleWebMapper;

    public UploadRuleController(UploadRuleService uploadRuleService,
                                UploadRuleWebMapper uploadRuleWebMapper) {
        this.uploadRuleService = uploadRuleService;
        this.uploadRuleWebMapper = uploadRuleWebMapper;
    }

    @GetMapping
    @RequiresPermission(authenticatedOnly = true, allowApiKey = true)
    public ApiResponse<UploadRuleResponse> detail(@RequestParam(required = false) @Size(max = 64) String moduleKey) {
        return ApiResponse.success(uploadRuleWebMapper.toResponse(uploadRuleService.getPageUploadRule(defaultModuleKey(moduleKey))));
    }

    @PutMapping
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "单号规则", actionType = "编辑上传命名规则")
    public ApiResponse<UploadRuleResponse> update(@RequestParam(required = false) @Size(max = 64) String moduleKey,
                                                  @Valid @RequestBody UploadRuleRequest request) {
        return ApiResponse.success(
                "更新成功",
                uploadRuleWebMapper.toResponse(uploadRuleService.updatePageUploadRule(
                        defaultModuleKey(moduleKey),
                        uploadRuleWebMapper.toCommand(request)
                ))
        );
    }

    private String defaultModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return "general-setting";
        }
        return moduleKey.trim();
    }
}
