package com.leo.erp.attachment.web;

import com.leo.erp.attachment.api.AttachmentManifestExportResult;
import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/attachments/manifests")
public class V2AttachmentManifestController {

    private final AttachmentManifestExporter exportService;

    public V2AttachmentManifestController(AttachmentManifestExporter exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/daily/export")
    @OperationLoggable(moduleName = "附件管理", actionType = "导出附件恢复清单")
    public AttachmentManifestExportResult exportDaily() {
        return exportService.exportDaily();
    }
}
