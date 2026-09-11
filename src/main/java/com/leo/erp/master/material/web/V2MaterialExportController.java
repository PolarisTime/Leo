package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.service.MaterialDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 商品资料导出资源接口。
 * 导出文件是资源表示：通过创建 material-exports 资源同步生成并返回文件，
 * 不再使用 /materials/export、/materials/export/csv 这类动作后缀。
 */
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/material-exports")
@Tag(name = "商品资料导出")
public class V2MaterialExportController {

    private static final String FORMAT_XLSX = "xlsx";
    private static final String FORMAT_CSV = "csv";
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType XLSX_MEDIA_TYPE = new MediaType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final MaterialDocumentService materialDocumentService;

    public V2MaterialExportController(MaterialDocumentService materialDocumentService) {
        this.materialDocumentService = materialDocumentService;
    }

    @PostMapping
    @Operation(summary = "创建商品资料导出文件")
    public ResponseEntity<byte[]> create(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = FORMAT_XLSX) String format) {
        String normalizedFormat = format == null ? FORMAT_XLSX : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedFormat) {
            case FORMAT_XLSX -> toDownloadResponse(
                    "material.xlsx", XLSX_MEDIA_TYPE, materialDocumentService.exportSpreadsheet(keyword));
            case FORMAT_CSV -> toDownloadResponse(
                    "materials.csv", CSV_MEDIA_TYPE, materialDocumentService.exportCsv(keyword));
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的导出格式: " + format);
        };
    }

    private ResponseEntity<byte[]> toDownloadResponse(String filename, MediaType contentType, byte[] content) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content);
    }
}
