package com.leo.erp.attachment.web;

import com.leo.erp.attachment.api.AttachmentManifestArchive;
import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 附件恢复清单导出资源接口。
 * 导出清单被建模为 attachment-manifest-exports 资源：创建资源即同步生成 gzip 清单文件并返回，
 * 不再使用 /attachments/manifests/daily/export 这类动作后缀。
 */
@Tag(name = "附件恢复清单导出")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/attachment-manifest-exports")
public class V2AttachmentManifestExportController {

    private final AttachmentManifestExporter exportService;

    public V2AttachmentManifestExportController(AttachmentManifestExporter exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "创建附件恢复清单导出文件",
            description = "同步生成并返回 gzip 格式的附件恢复清单文件，同时持久化到附件存储；重复提交建议携带 Idempotency-Key。")
    @IdempotencyRequired
    @PostMapping
    @OperationLoggable(moduleName = "附件管理", actionType = "导出附件恢复清单")
    public ResponseEntity<byte[]> create() {
        AttachmentManifestArchive archive = exportService.exportDailyArchive();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(archive.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.parseMediaType(archive.contentType()))
                .contentLength(archive.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(archive.content());
    }
}
