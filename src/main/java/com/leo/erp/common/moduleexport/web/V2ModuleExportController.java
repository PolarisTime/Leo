package com.leo.erp.common.moduleexport.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.moduleexport.service.ModuleExportService;
import com.leo.erp.common.moduleexport.web.dto.ModuleExportRequest;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 业务单据导出资源接口。
 *
 * <p>导出被建模为资源：创建 {@code module-exports} 资源即同步生成并返回 XLSX 文件表示，
 * 不再使用 {@code /{module}/export} 这类动作后缀。请求体中的 {@code recordIds}
 * 支持按记录 id 集合精确过滤（「导出选中 N 条」的真实来源），省略时导出整模块。</p>
 */
@Tag(name = "业务单据导出")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/module-exports")
public class V2ModuleExportController {

    private final ModuleExportService moduleExportService;

    public V2ModuleExportController(ModuleExportService moduleExportService) {
        this.moduleExportService = moduleExportService;
    }

    @Operation(
            summary = "创建业务单据导出文件",
            description = """
                    同步生成 XLSX 导出文件（200 + Content-Type/Content-Disposition）。
                    请求体 moduleKey 必须是受支持的业务单据模块；recordIds 为可选的十进制雪花 ID 字符串数组，
                    省略时导出该模块全部未删除记录（受 leo.excel.max-export-rows 限制），
                    提供时必须非空且不超过 1000 条，且集合内每条记录都必须存在于该模块。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件生成成功",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "请求体格式错误，或雪花 ID 未使用十进制字符串",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "403", description = "缺少 module-exports:export 或模块读取权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "recordIds 中存在未删除记录里查不到的 id",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "模块不支持、recordIds 为空集合/超长/重复，或导出行数超过上限",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(PermissionCodes.MODULE_EXPORTS_EXPORT)
    public ResponseEntity<byte[]> create(@Valid @RequestBody ModuleExportRequest payload) {
        FileDownloadResponse file = moduleExportService.export(payload.moduleKey(), payload.recordIds());
        return toDownloadResponse(file);
    }

    private ResponseEntity<byte[]> toDownloadResponse(FileDownloadResponse file) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.content());
    }
}
