package com.leo.erp.common.moduleexport.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.ResponseSupport;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactAccess;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.web.ExportArtifactResponse;
import com.leo.erp.common.export.web.ExportArtifactResponses;
import com.leo.erp.common.moduleexport.service.ModuleExportArtifactService;
import com.leo.erp.common.moduleexport.web.dto.ModuleExportRequest;
import com.leo.erp.common.validation.SnowflakeId;
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
 * 业务单据导出请求资源接口。
 *
 * <p>导出被建模为「请求/产物资源」，不再用 {@code POST /module-exports} 这类同步动作端点表达：</p>
 * <ol>
 *     <li>{@code POST /module-export-requests} 创建导出请求，同步生成文件；
 *         成功返回 201 + {@code Location}，响应体含雪花 id 与 {@code contentPath}；</li>
 *     <li>{@code GET /module-export-requests/{id}} 回读资源元数据；</li>
 *     <li>{@code GET /module-export-requests/{id}/content} 读取文件内容（正确
 *         {@code Content-Type}/{@code Content-Disposition}/{@code Content-Length}）。</li>
 * </ol>
 *
 * <p>产物是有界缓存：过期（默认 30 分钟）或实例重启后回读返回 404，客户端重新创建即可。</p>
 */
@Tag(name = "业务单据导出")
@RestController
@Validated
@com.leo.erp.common.idempotent.IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/module-export-requests")
public class ModuleExportRequestController {

    static final String COLLECTION_PATH = "/module-export-requests";

    private final ModuleExportArtifactService moduleExportArtifactService;
    private final ExportArtifactStore artifactStore;

    public ModuleExportRequestController(ModuleExportArtifactService moduleExportArtifactService,
                                           ExportArtifactStore artifactStore) {
        this.moduleExportArtifactService = moduleExportArtifactService;
        this.artifactStore = artifactStore;
    }

    @Operation(
            summary = "创建业务单据导出请求",
            description = """
                    同步生成 XLSX 导出产物并登记为资源；成功 201 + Location。
                    moduleKey 必须是受支持的模块；recordIds 为可选的十进制雪花 ID 字符串数组，
                    省略时导出该模块全部未删除记录（受 leo.excel.max-export-rows 限制），
                    提供时必须非空、不重复且不超过 1000 条，且每条记录都必须存在。
                    重复提交请携带 Idempotency-Key。产物默认保留 30 分钟，过期后 content 端点返回 404。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "导出产物已创建（Location 指向本资源）"),
            @ApiResponse(responseCode = "400", description = "请求体格式错误，或雪花 ID 未使用十进制字符串",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "403", description = "缺少 module-exports:export 或模块读取权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "recordIds 中存在未删除记录里查不到的 id",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "模块不支持、recordIds 为空集合/超长/重复、行数超上限或导出结果过大",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "429", description = "并发导出数达到上限（快速失败，携带 Retry-After）",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(PermissionCodes.MODULE_EXPORTS_EXPORT)
    public ResponseEntity<ExportArtifactResponse> create(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody ModuleExportRequest payload) {
        ExportArtifact artifact = moduleExportArtifactService.create(payload, requesterId(principal));
        return ResponseSupport.created(
                COLLECTION_PATH,
                ExportArtifactResponse.of(artifact, COLLECTION_PATH)
        );
    }

    @Operation(summary = "查询业务单据导出资源",
            description = "返回导出产物的元数据（文件名、媒体类型、字节数、过期时间、contentPath）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出资源元数据"),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.MODULE_EXPORTS_EXPORT)
    public ExportArtifactResponse detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                         @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponse.of(requireArtifact(principal, id), COLLECTION_PATH);
    }

    @Operation(summary = "读取业务单据导出文件内容",
            description = "文件内容是资源表示：200 + XLSX 媒体类型与 Content-Disposition 附件头。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件内容",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/content")
    @RequirePermission(PermissionCodes.MODULE_EXPORTS_EXPORT)
    public ResponseEntity<Resource> content(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponses.content(requireArtifact(principal, id));
    }

    private ExportArtifact requireArtifact(SecurityPrincipal principal, Long id) {
        return ExportArtifactAccess.require(
                artifactStore, ExportArtifactKinds.MODULE_EXPORT, id, requesterId(principal));
    }

    private Long requesterId(SecurityPrincipal principal) {
        return principal == null ? null : principal.id();
    }
}
