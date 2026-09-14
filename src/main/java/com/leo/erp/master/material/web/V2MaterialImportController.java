package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.web.dto.MaterialImportPreviewResponse;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * 商品资料导入资源接口。
 * 导入是创建操作：通过创建 material-imports 资源提交文件，返回导入结果资源。
 * 不再使用 /materials/import、/materials/import/csv 这类动作后缀。
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/material-imports")
@Tag(name = "商品资料导入")
public class V2MaterialImportController {

    private static final String FORMAT_XLSX = "xlsx";
    private static final String FORMAT_CSV = "csv";

    private final MaterialImportFileAdapter materialImportFileAdapter;

    public V2MaterialImportController(MaterialImportFileAdapter materialImportFileAdapter) {
        this.materialImportFileAdapter = materialImportFileAdapter;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @V2Created
    @Operation(summary = "创建商品资料导入任务")
    @RequirePermission(PermissionCodes.MATERIAL_IMPORTS_IMPORT)
    public ResponseEntity<MaterialImportResultResponse> create(@RequestParam("file") MultipartFile file,
                                                               @RequestParam(required = false) String format)
            throws IOException {
        return V2ResponseSupport.created("/material-imports", importFile(file, format));
    }

    @PostMapping(value = "/previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @V2Created
    @Operation(summary = "创建商品资料导入预览资源（dry-run，不落库）")
    @RequirePermission(PermissionCodes.MATERIAL_IMPORTS_PREVIEW)
    public ResponseEntity<MaterialImportPreviewResponse> preview(@RequestParam("file") MultipartFile file,
                                                                 @RequestParam(required = false) String format)
            throws IOException {
        MaterialImportPreviewResponse previewResponse;
        if (FORMAT_CSV.equalsIgnoreCase(resolveFormat(file, format))) {
            previewResponse = materialImportFileAdapter.previewCsv(file);
        } else {
            previewResponse = materialImportFileAdapter.previewSpreadsheet(file);
        }
        return V2ResponseSupport.created("/material-imports/previews", previewResponse);
    }

    private MaterialImportResultResponse importFile(MultipartFile file, String format) throws IOException {
        if (FORMAT_CSV.equalsIgnoreCase(resolveFormat(file, format))) {
            return materialImportFileAdapter.importCsv(file);
        }
        return materialImportFileAdapter.importSpreadsheet(file);
    }

    private String resolveFormat(MultipartFile file, String format) {
        if (format != null && !format.isBlank()) {
            String normalized = format.trim().toLowerCase(Locale.ROOT);
            if (!FORMAT_XLSX.equals(normalized) && !FORMAT_CSV.equals(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的导入格式: " + format);
            }
            return normalized;
        }
        String filename = file == null ? null : file.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith("." + FORMAT_CSV)) {
            return FORMAT_CSV;
        }
        String contentType = file == null ? null : file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains(FORMAT_CSV)) {
            return FORMAT_CSV;
        }
        return FORMAT_XLSX;
    }
}
