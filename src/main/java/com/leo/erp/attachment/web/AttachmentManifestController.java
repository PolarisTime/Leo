package com.leo.erp.attachment.web;

import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.attachment.api.AttachmentManifestExportResult;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/attachments/manifests")
public class AttachmentManifestController {

    private final AttachmentManifestExporter exportService;

    public AttachmentManifestController(AttachmentManifestExporter exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/daily/export")
    @OperationLoggable(moduleName = "附件管理", actionType = "导出附件恢复清单")
    public ApiResponse<AttachmentManifestExportResult> exportDaily() {
        return ApiResponse.success("附件恢复清单导出成功", exportService.exportDaily());
    }
}
