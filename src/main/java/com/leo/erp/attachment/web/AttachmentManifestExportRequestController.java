package com.leo.erp.attachment.web;

import com.leo.erp.attachment.service.AttachmentManifestExportArtifactService;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactAccess;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.web.ExportArtifactResponse;
import com.leo.erp.common.export.web.ExportArtifactResponses;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 附件恢复清单导出请求资源接口。
 *
 * <p>清单生成被建模为请求/产物资源：{@code POST /attachment-manifest-export-requests}
 * 同步生成 gzip 清单并登记产物，返回 201 + Location；{@code GET /{id}} 回读元数据；
 * {@code GET /{id}/content} 读取 gzip 内容。同步命令式的旧端点
 * {@code POST /attachment-manifest-exports} 已删除。</p>
 */
@Tag(name = "附件恢复清单导出")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/attachment-manifest-export-requests")
public class AttachmentManifestExportRequestController {

    static final String COLLECTION_PATH = "/attachment-manifest-export-requests";
    static final String GZIP_MEDIA_TYPE = "application/gzip";

    private final AttachmentManifestExportArtifactService attachmentManifestExportArtifactService;
    private final ExportArtifactStore artifactStore;

    public AttachmentManifestExportRequestController(
            AttachmentManifestExportArtifactService attachmentManifestExportArtifactService,
            ExportArtifactStore artifactStore) {
        this.attachmentManifestExportArtifactService = attachmentManifestExportArtifactService;
        this.artifactStore = artifactStore;
    }

    @Operation(
            summary = "创建附件恢复清单导出请求",
            description = """
                    同步生成 gzip 附件恢复清单（jsonl.gz）并登记为导出资源，同时写入附件存储；
                    成功 201 + Location。重复提交请携带 Idempotency-Key。
                    产物默认保留 30 分钟，过期后 content 端点返回 404。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "导出产物已创建（Location 指向本资源）"),
            @ApiResponse(responseCode = "403", description = "缺少 attachment-manifest-exports:export 权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "429", description = "并发导出数达到上限（快速失败，携带 Retry-After）",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping
    @OperationLoggable(moduleName = "附件管理", actionType = "导出附件恢复清单")
    @RequirePermission(PermissionCodes.ATTACHMENT_MANIFEST_EXPORTS_EXPORT)
    public ResponseEntity<ExportArtifactResponse> create(
            @AuthenticationPrincipal SecurityPrincipal principal) {
        ExportArtifact artifact = attachmentManifestExportArtifactService.create(requesterId(principal));
        return ResponseSupport.created(
                COLLECTION_PATH,
                ExportArtifactResponse.of(artifact, COLLECTION_PATH)
        );
    }

    @Operation(summary = "查询附件恢复清单导出资源",
            description = "返回导出产物的元数据（文件名、媒体类型、字节数、过期时间、contentPath）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出资源元数据"),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.ATTACHMENT_MANIFEST_EXPORTS_EXPORT)
    public ExportArtifactResponse detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                         @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponse.of(requireArtifact(principal, id), COLLECTION_PATH);
    }

    @Operation(summary = "读取附件恢复清单文件内容",
            description = "文件内容是资源表示：200 + application/gzip 与 Content-Disposition 附件头。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件内容",
                    content = @Content(mediaType = GZIP_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/content")
    @RequirePermission(PermissionCodes.ATTACHMENT_MANIFEST_EXPORTS_EXPORT)
    public ResponseEntity<Resource> content(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponses.content(requireArtifact(principal, id));
    }

    private ExportArtifact requireArtifact(SecurityPrincipal principal, Long id) {
        return ExportArtifactAccess.require(
                artifactStore, ExportArtifactKinds.ATTACHMENT_MANIFEST_EXPORT, id, requesterId(principal));
    }

    private Long requesterId(SecurityPrincipal principal) {
        return principal == null ? null : principal.id();
    }
}
