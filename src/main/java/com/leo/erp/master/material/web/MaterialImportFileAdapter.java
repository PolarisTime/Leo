package com.leo.erp.master.material.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.dto.ImportResult;
import com.leo.erp.common.excel.service.ExcelImportService;
import com.leo.erp.master.material.service.MaterialCsvImportResult;
import com.leo.erp.master.material.service.MaterialCsvImportService;
import com.leo.erp.master.material.service.MaterialSpreadsheetImportService;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import com.leo.erp.master.material.web.dto.MaterialImportFailureResponse;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Component
public class MaterialImportFileAdapter {

    private final ExcelImportService excelImportService;
    private final MaterialSpreadsheetImportService spreadsheetImportService;
    private final MaterialCsvImportService csvImportService;

    public MaterialImportFileAdapter(ExcelImportService excelImportService,
                                     MaterialSpreadsheetImportService spreadsheetImportService,
                                     MaterialCsvImportService csvImportService) {
        this.excelImportService = excelImportService;
        this.spreadsheetImportService = spreadsheetImportService;
        this.csvImportService = csvImportService;
    }

    public ImportResult importSpreadsheet(MultipartFile file) throws IOException {
        List<MaterialImportDTO> rows = excelImportService.parseAndValidate(file, MaterialImportDTO.class);
        return spreadsheetImportService.importRows(rows);
    }

    public MaterialImportResultResponse importCsv(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        MaterialCsvImportResult result = csvImportService.importBytes(file.getBytes());
        List<MaterialImportFailureResponse> failures = result.failures().stream()
                .map(failure -> new MaterialImportFailureResponse(
                        failure.rowNumber(),
                        failure.materialCode(),
                        failure.reason()
                ))
                .toList();
        return new MaterialImportResultResponse(
                result.totalRows(),
                result.successCount(),
                result.createdCount(),
                result.updatedCount(),
                result.skippedCount(),
                result.failedCount(),
                failures
        );
    }
}
