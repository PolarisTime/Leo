package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class MaterialSpreadsheetImportService {

    private final MaterialImportProcessor importProcessor;

    public MaterialSpreadsheetImportService(MaterialImportProcessor importProcessor) {
        this.importProcessor = importProcessor;
    }

    /**
     * 单行导入轨迹：outcome 为 null 表示该行失败，failReason 记录原因。
     */
    public record ImportRowTrace(int rowNumber, MaterialImportProcessor.ImportOutcome outcome, Material material,
                                 String failReason) {

        public String outcomeName() {
            return outcome == null ? "FAILED" : outcome.name();
        }
    }

    public record SpreadsheetImportResult(
            int totalRows,
            int successCount,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int failCount,
            List<ImportRowTrace> rows
    ) {
    }

    @Transactional
    public SpreadsheetImportResult importRows(List<MaterialImportDTO> rows) {
        MaterialImportProcessor.ImportSession session = importProcessor.start(
                rows.stream().map(this::identity).toList()
        );

        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failCount = 0;
        List<ImportRowTrace> traces = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            int rowNumber = index + 2;
            // 行级失败只记录并跳过，不中断其余行的导入（与 CSV 导入路径行为一致）。
            try {
                MaterialImportData importData = toImportData(rows.get(index), rowNumber);
                MaterialImportProcessor.ImportRowResult result = importProcessor.importRow(
                        session,
                        importData,
                        rowNumber
                );
                traces.add(new ImportRowTrace(rowNumber, result.outcome(), result.material(), null));
                switch (result.outcome()) {
                    case CREATED -> createdCount++;
                    case UPDATED -> updatedCount++;
                    case SKIPPED -> skippedCount++;
                }
            } catch (BusinessException exception) {
                failCount++;
                traces.add(new ImportRowTrace(rowNumber, null, null, exception.getMessage()));
            } catch (DataIntegrityViolationException exception) {
                failCount++;
                traces.add(new ImportRowTrace(rowNumber, null, null, "保存失败，请检查该行数据"));
            } catch (DataAccessException exception) {
                failCount++;
                traces.add(new ImportRowTrace(rowNumber, null, null, "保存失败，请检查该行数据"));
            }
        }
        int successCount = createdCount + updatedCount;
        return new SpreadsheetImportResult(
                rows.size(), successCount, createdCount, updatedCount, skippedCount, failCount,
                List.copyOf(traces)
        );
    }

    private MaterialIdentityService.Identity identity(MaterialImportDTO row) {
        return importProcessor.identity(row.brand(), row.material(), row.spec(), row.length());
    }

    private MaterialImportData toImportData(MaterialImportDTO row, int rowNumber) {
        boolean expense = MaterialImportData.TYPE_EXPENSE.equals(
                row.materialType() == null ? "" : row.materialType().trim());
        return new MaterialImportData(
                row.materialCode(),
                expense ? "" : row.brand(),
                row.material(),
                expense ? "附加费用" : row.category(),
                expense ? "" : row.spec(),
                expense ? "" : row.length(),
                row.unit(),
                row.quantityUnit(),
                expense ? java.math.BigDecimal.ZERO : parseBigDecimalOrZero(row.pieceWeightTon(), rowNumber, "件重(吨)"),
                expense ? 0 : parseIntegerOrZero(row.piecesPerBundle(), rowNumber, "每件支数"),
                parseBigDecimalOrZero(row.unitPrice(), rowNumber, "单价"),
                row.remark(),
                expense ? MaterialImportData.TYPE_EXPENSE : MaterialImportData.TYPE_PHYSICAL
        );
    }

    private BigDecimal parseBigDecimalOrZero(String value, int rowNumber, String label) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw invalidNumber(rowNumber, label);
        }
    }

    private int parseIntegerOrZero(String value, int rowNumber, String label) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw invalidNumber(rowNumber, label);
        }
    }

    private BusinessException invalidNumber(int rowNumber, String label) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "第" + rowNumber + "行【" + label + "】格式不正确"
        );
    }
}
