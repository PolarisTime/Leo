package com.leo.erp.system.oss.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.oss.service.OssSettingService;
import com.leo.erp.system.oss.web.dto.OssCorsConfigureRequest;
import com.leo.erp.system.oss.web.dto.OssOperationResult;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import com.leo.erp.system.oss.web.dto.OssSettingResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<OssSettingResponse> current() {
        return ApiResponse.success(ossSettingService.current());
    }

    @PutMapping
    @OperationLoggable(moduleName = "OSS 设置", actionType = "保存")
    public ApiResponse<OssSettingResponse> save(@Valid @RequestBody OssSettingRequest request) {
        return ApiResponse.success("保存成功", ossSettingService.save(request));
    }

    @PostMapping("/storage-test")
    @OperationLoggable(moduleName = "OSS 设置", actionType = "测试存储")
    public ApiResponse<OssOperationResult> testStorage(@Valid @RequestBody OssSettingRequest request) {
        return ApiResponse.success("测试完成", ossSettingService.testStorage(request));
    }

    @PostMapping("/cors")
    @OperationLoggable(moduleName = "OSS 设置", actionType = "配置 CORS")
    public ApiResponse<OssOperationResult> configureCors(@Valid @RequestBody OssCorsConfigureRequest request) {
        return ApiResponse.success("配置完成", ossSettingService.configureCors(request));
    }
}
