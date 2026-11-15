package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordOutputResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import java.util.Base64;

/**
 * 打印导出资源接口。
 *
 * <p>按「表示」拆成两个资源路径，避免同一 URL 返回两种 Content-Type：</p>
 * <ul>
 *   <li>{@code POST /v2.0/print-exports} → 打印脚本/数据表示（200 + application/json，非 PDF 模板）；</li>
 *   <li>{@code POST /v2.0/print-export-files} → PDF 文件表示（200 + application/pdf，PDF_FORM 模板）。</li>
 * </ul>
 * <p>两者都是同步生成命令（不是可回读资源，不发 Location）；模板类型与端点不匹配时返回 406。</p>
 */
@Tag(name = "打印导出")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX)
public class PrintExportController {

    private final PrintOutputService printOutputService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;

    public PrintExportController(PrintOutputService printOutputService,
                                   ExportConcurrencyGuard exportConcurrencyGuard) {
        this.printOutputService = printOutputService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
    }

    /**
     * 打印脚本表示（非 PDF 模板）：200 + JSON 资源表示，不发 Location。
     * <p>PDF 表单模板必须走 {@link #createPdf(PrintRecordRequest)}；走错端点返回 406。</p>
     */
    @Operation(summary = "生成打印脚本表示（200 + JSON；PDF 模板请使用 /print-export-files）")
    @PostMapping("/print-exports")
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    @RequirePermission(PermissionCodes.PRINT_EXPORTS_PRINT)
    public ResponseEntity<PrintRecordOutputResponse> createScript(
            @Valid @RequestBody @NotNull PrintRecordRequest payload) {
        PrintOutput output = generate(payload);
        if (output.kind() == PrintOutput.Kind.PDF) {
            throw new BusinessException(ErrorCode.NOT_ACCEPTABLE,
                    "该模板为 PDF 表单，请请求 POST " + ApiVersion.PREFIX + "/print-export-files");
        }
        return ResponseEntity.ok(PrintRecordOutputResponse.from(output));
    }

    /**
     * PDF 文件表示（PDF_FORM 模板）：200 + application/pdf，不发 Location。
     * <p>脚本模板必须走 {@link #createScript(PrintRecordRequest)}；走错端点返回 406。</p>
     */
    @Operation(summary = "生成打印 PDF 文件（200 + application/pdf；脚本模板请使用 /print-exports）",
            responses = @ApiResponse(responseCode = "200", description = "打印 PDF 文件",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @PostMapping("/print-export-files")
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    @RequirePermission(PermissionCodes.PRINT_EXPORTS_PRINT)
    public ResponseEntity<byte[]> createPdf(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
        PrintOutput output = generate(payload);
        if (output.kind() != PrintOutput.Kind.PDF) {
            throw new BusinessException(ErrorCode.NOT_ACCEPTABLE,
                    "该模板为套打脚本，请请求 POST " + ApiVersion.PREFIX + "/print-exports");
        }
        return pdfFileResponse(output);
    }

    private PrintOutput generate(PrintRecordRequest payload) {
        // 打印/PDF 生成同属 CPU 密集导出路径，纳入同一闸门（见 ExportConcurrencyGuard）；
        // 闸门在服务开启事务之前取额度，等待期间不占用数据库连接。
        return exportConcurrencyGuard.execute("print-output",
                () -> printOutputService.generateFromRecord(
                        payload.templateId(),
                        payload.moduleKey(),
                        payload.recordId(),
                        payload.resolvedPrintOptions()
                ));
    }

    private ResponseEntity<byte[]> pdfFileResponse(PrintOutput output) {
        byte[] pdf = Base64.getDecoder().decode(output.pdfBase64());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(output.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(output.contentType()))
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
