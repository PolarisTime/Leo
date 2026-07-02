package com.leo.erp.system.oss.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.oss.service.OssSettingService;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import com.leo.erp.system.oss.web.dto.OssSettingResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/system/oss-settings")
public class OssSettingController {

    private final OssSettingService ossSettingService;

    public OssSettingController(OssSettingService ossSettingService) {
        this.ossSettingService = ossSettingService;
    }

    @GetMapping
    @RequiresPermission(resource = "general-setting", action = "read")
    public ApiResponse<OssSettingResponse> current() {
        return ApiResponse.success(ossSettingService.current());
    }

    @PutMapping
    @RequiresPermission(resource = "general-setting", action = "update")
    @OperationLoggable(moduleName = "OSS 设置", actionType = "保存")
    public ApiResponse<OssSettingResponse> save(@Valid @RequestBody OssSettingRequest request) {
        return ApiResponse.success("保存成功", ossSettingService.save(request));
    }
}
