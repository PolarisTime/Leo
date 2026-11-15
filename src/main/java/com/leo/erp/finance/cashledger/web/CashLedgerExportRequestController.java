package com.leo.erp.finance.cashledger.web;

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
import com.leo.erp.finance.cashledger.service.CashLedgerExportArtifactService;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerExportRequest;
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
 * 资金流水导出请求资源接口。
 *
 * <p>旧实现用 {@code GET /cash-ledger/export} 直接触发导出并返回文件；它把「产生资源」伪装成
 * 安全读取，且 GET 不适合携带复杂筛选条件。资源化后：</p>
 * <ol>
 *     <li>{@code POST /cash-ledger-export-requests} 用请求体表达筛选条件，创建导出资源并返回
 *         201 + Location（同步生成完成，产物已可回读）；</li>
 *     <li>{@code GET /cash-ledger-export-requests/{id}} 回读元数据；</li>
 *     <li>{@code GET /cash-ledger-export-requests/{id}/content} 读取 XLSX 文件内容。</li>
 * </ol>
 *
 * <p>产物默认保留 30 分钟，过期或实例重启后回读返回 404；并发额度耗尽返回 429（带 Retry-After），
 * 行数超过上限返回 422 并提示缩小筛选范围。</p>
 */
@Tag(name = "资金流水导出")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/cash-ledger-export-requests")
public class CashLedgerExportRequestController {

    static final String COLLECTION_PATH = "/cash-ledger-export-requests";

    private final CashLedgerExportArtifactService cashLedgerExportArtifactService;
    private final ExportArtifactStore artifactStore;

    public CashLedgerExportRequestController(CashLedgerExportArtifactService cashLedgerExportArtifactService,
                                               ExportArtifactStore artifactStore) {
        this.cashLedgerExportArtifactService = cashLedgerExportArtifactService;
        this.artifactStore = artifactStore;
    }

    @Operation(
            summary = "创建资金流水导出请求",
            description = """
                    按请求体筛选条件同步生成资金流水 XLSX 导出产物并登记为资源；成功 201 + Location。
                    筛选字段语义与 GET /cash-ledger 一致；结算主体必填，日期为 ISO-8601。
                    重复提交请携带 Idempotency-Key。产物默认保留 30 分钟，过期后 content 端点返回 404。"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "导出产物已创建（Location 指向本资源）"),
            @ApiResponse(responseCode = "400", description = "请求体格式错误，或雪花 ID 未使用十进制字符串",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "403", description = "缺少 cash-ledger:export 权限",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "筛选条件不合法、导出行数超过上限或导出结果过大",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "429", description = "并发导出数达到上限（快速失败，携带 Retry-After）",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(PermissionCodes.CASH_LEDGER_EXPORT)
    public ResponseEntity<ExportArtifactResponse> create(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CashLedgerExportRequest payload) {
        ExportArtifact artifact = cashLedgerExportArtifactService.create(
                payload.settlementCompanyId(),
                payload.startDate(),
                payload.endDate(),
                payload.counterpartyType(),
                payload.counterpartyId(),
                payload.flowType(),
                payload.keyword(),
                requesterId(principal)
        );
        return ResponseSupport.created(
                COLLECTION_PATH,
                ExportArtifactResponse.of(artifact, COLLECTION_PATH)
        );
    }

    @Operation(summary = "查询资金流水导出资源",
            description = "返回导出产物的元数据（文件名、媒体类型、字节数、过期时间、contentPath）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出资源元数据"),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.CASH_LEDGER_EXPORT)
    public ExportArtifactResponse detail(@AuthenticationPrincipal SecurityPrincipal principal,
                                         @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponse.of(requireArtifact(principal, id), COLLECTION_PATH);
    }

    @Operation(summary = "读取资金流水导出文件内容",
            description = "文件内容是资源表示：200 + XLSX 媒体类型与 Content-Disposition 附件头。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导出文件内容",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "导出结果不存在或已过期",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @GetMapping("/{id}/content")
    @RequirePermission(PermissionCodes.CASH_LEDGER_EXPORT)
    public ResponseEntity<Resource> content(@AuthenticationPrincipal SecurityPrincipal principal,
                                            @PathVariable @SnowflakeId Long id) {
        return ExportArtifactResponses.content(requireArtifact(principal, id));
    }

    private ExportArtifact requireArtifact(SecurityPrincipal principal, Long id) {
        return ExportArtifactAccess.require(
                artifactStore, ExportArtifactKinds.CASH_LEDGER_EXPORT, id, requesterId(principal));
    }

    private Long requesterId(SecurityPrincipal principal) {
        return principal == null ? null : principal.id();
    }
}
