package com.leo.erp.system.printtemplate.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import com.leo.erp.system.printtemplate.service.PrintOutput;
import com.leo.erp.system.printtemplate.service.PrintOutputService;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordOutputResponse;
import com.leo.erp.system.printtemplate.web.dto.PrintRecordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 * 打印导出被建模为 print-exports 资源：创建资源即同步生成打印结果，
 * PDF 模板返回二进制文件（201 Created + Content-Type/Content-Disposition），
 * 其余模板返回打印脚本资源表示（201 Created）。
 */
@Tag(name = "打印导出")
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/print-exports")
public class V2PrintExportController {

    private final PrintOutputService printOutputService;

    public V2PrintExportController(PrintOutputService printOutputService) {
        this.printOutputService = printOutputService;
    }

    @Operation(summary = "创建打印导出资源（同步返回 PDF 文件或打印脚本资源表示）")
    @PostMapping
    @OperationLoggable(
            moduleName = "打印",
            moduleNameField = "moduleKey",
            actionType = "打印",
            businessNoFields = {"businessNo"},
            recordIdField = "recordId",
            moduleKeyField = "moduleKey"
    )
    public ResponseEntity<?> create(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
        PrintOutput output = printOutputService.generateFromRecord(
                payload.templateId(),
                payload.moduleKey(),
                payload.recordId(),
                payload.resolvedPrintOptions()
        );
        if (output.kind() == PrintOutput.Kind.PDF) {
            return pdfFileResponse(output);
        }
        return V2ResponseSupport.created("/print-exports", PrintRecordOutputResponse.from(output));
    }

    private ResponseEntity<byte[]> pdfFileResponse(PrintOutput output) {
        byte[] pdf = Base64.getDecoder().decode(output.pdfBase64());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(output.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.parseMediaType(output.contentType()))
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
