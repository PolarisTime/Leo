package com.leo.erp.master.material.web;

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
import com.leo.erp.master.material.service.MaterialExportArtifactService;
import com.leo.erp.master.material.web.dto.MaterialExportRequest;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品资料导出请求资源接口。
 *
 * <p>导出被建模为请求/产物资源：{@code POST /material-export-requests} 同步生成文件并返回
 * 201 + Location；{@code GET /{id}} 回读元数据；{@code GET /{id}/content} 读取文件内容。
 * 动作式的旧端点 {@code POST /material-exports} 已删除。</p>
 */
@Tag(name = "商品资料导出")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/material-export-requests")
public class MaterialExportRequestController {

    static final String COLLECTION_PATH = "/material-export-requests";

    private final MaterialExportArtifactService materialExportArtifactService;
    private final ExportArtifactStore artifactStore;

    public MaterialExportRequestController(MaterialExportArtifactService materialExportArtifactService,
                                             ExportArtifactStore artifactStore) {
        this.materialExportArtifactService = materialExportArtifactService;
        this.artifactStore = artifactStore;
    }

    @Operation(
            summary = "创建商品资料导出请求",
            description = """
                    同步生成商品资料导出产物并登记为资源；成功 201 + Location。
                    keyword 可选，语义与列表筛选一致；format 支持 xlsx（默认）与 csv，其它值返回 422。
                    重复提交请携带 Idempotency-Key。产物默认保留 30 分钟，过期后 content 端点返回 404。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "导出产物已创建（Location 指向本资源）"),
            @ApiResponse(responseCode = "403", description = "缺少 material-exports:export 权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "导出格式不受支持，或导出结果过大",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "429", description = "并发导出数达到上限（快速失败，携带 Retry-After）",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(PermissionCodes.MATERIAL_EXPORTS_EXPORT)
    public ResponseEntity<ExportArtifactResponse> create(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody MaterialExportRequest payload) {
        ExportArtifact artifact = materialExportArtifactService.create(
                payload.keyword(), payload.format(), requesterId(principal));
        return ResponseSupport.created(
                COLLECTION_PATH,
                ExportArtifactResponse.of(artifact, COLLECTION_PATH)
        );
    }

    @Operation(summary = "查询商品资料导出资源",
            description = "返回导出产物的元数据（文件名、媒体类型、字节数、过期时间、contentPath）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出资源元数据"),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.MATERIAL_EXPORTS_EXPORT)
    public ExportArtifactResponse detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                         @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponse.of(requireArtifact(principal, id), COLLECTION_PATH);
    }

    @Operation(summary = "读取商品资料导出文件内容",
            description = "文件内容是资源表示：200 + 对应的 XLSX/CSV 媒体类型与 Content-Disposition 附件头。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件内容",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/content")
    @RequirePermission(PermissionCodes.MATERIAL_EXPORTS_EXPORT)
    public ResponseEntity<Resource> content(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponses.content(requireArtifact(principal, id));
    }

    private ExportArtifact requireArtifact(SecurityPrincipal principal, Long id) {
        return ExportArtifactAccess.require(
                artifactStore, ExportArtifactKinds.MATERIAL_EXPORT, id, requesterId(principal));
    }

    private Long requesterId(SecurityPrincipal principal) {
        return principal == null ? null : principal.id();
    }
}
