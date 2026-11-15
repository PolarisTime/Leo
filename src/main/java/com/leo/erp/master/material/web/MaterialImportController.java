package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.idempotent.IdempotencyRequired;
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
 * 商品资料导入接口。
 *
 * <p>导入是<b>同步命令</b>，不是可回读资源：提交文件后返回导入结果（200），不返回 201、
 * 也不发 Location。dry-run 预览同样是同步命令（200）。路径使用名词化的
 * {@code /material-imports} 与 {@code /material-imports/previews}，不再使用
 * {@code /materials/import} 这类动作后缀。</p>
 */
@RestController
@Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.PREFIX + "/material-imports")
@Tag(name = "商品资料导入")
public class MaterialImportController {

    private static final String FORMAT_XLSX = "xlsx";
    private static final String FORMAT_CSV = "csv";

    private final MaterialImportFileAdapter materialImportFileAdapter;

    public MaterialImportController(MaterialImportFileAdapter materialImportFileAdapter) {
        this.materialImportFileAdapter = materialImportFileAdapter;
    }

    /**
     * 导入商品资料（同步命令）。
     * <p>导入结果不是可回读资源（响应体只有计数与明细，没有资源 id）：成功返回 200 + 导入结果，
     * 不返回 201，也不发 Location。需要「导入任务」可回读时再单独引入持久化任务资源。</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入商品资料（同步命令，200 + 导入结果）")
    @RequirePermission(PermissionCodes.MATERIAL_IMPORTS_IMPORT)
    public ResponseEntity<MaterialImportResultResponse> create(@RequestParam("file") MultipartFile file,
                                                               @RequestParam(required = false) String format)
            throws IOException {
        return ResponseEntity.ok(importFile(file, format));
    }

    /**
     * 导入预览（dry-run）端点：只解析文件并返回将要发生的变化，不落库、不写历史。
     * <p>dry-run 不是创建资源：成功返回 200 + 预览结果，不返回 201，也不发 Location。</p>
     */
    @PostMapping(value = "/previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "预览商品资料导入（dry-run，不落库，200 + 预览结果）")
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
        return ResponseEntity.ok(previewResponse);
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
