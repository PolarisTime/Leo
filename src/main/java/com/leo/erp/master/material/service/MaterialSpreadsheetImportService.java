package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.dto.ImportResult;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class MaterialSpreadsheetImportService {

    private final MaterialImportProcessor importProcessor;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;

    public MaterialSpreadsheetImportService(MaterialImportProcessor importProcessor,
                                            TradeItemMaterialSupport tradeItemMaterialSupport) {
        this.importProcessor = importProcessor;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
    }

    @Transactional
    public ImportResult importRows(List<MaterialImportDTO> rows) {
        MaterialImportProcessor.ImportSession session = importProcessor.start(
                rows.stream().map(this::identity).toList()
        );

        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<Material> successRows = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            int rowNumber = index + 2;
            MaterialImportData importData = toImportData(rows.get(index), rowNumber);
            MaterialImportProcessor.ImportRowResult result = importProcessor.importRow(
                    session,
                    importData,
                    rowNumber
            );
            switch (result.outcome()) {
                case CREATED -> createdCount++;
                case UPDATED -> updatedCount++;
                case SKIPPED -> skippedCount++;
            }
            if (result.outcome() != MaterialImportProcessor.ImportOutcome.SKIPPED) {
                successRows.add(result.material());
            }
        }
        if (!successRows.isEmpty()) {
            tradeItemMaterialSupport.evictCache();
        }
        int successCount = createdCount + updatedCount;
        return new ImportResult(
                rows.size(), successCount, createdCount, updatedCount, skippedCount, 0,
                List.of(), new ArrayList<>(successRows)
        );
    }

    private MaterialIdentityService.Identity identity(MaterialImportDTO row) {
        return importProcessor.identity(row.brand(), row.material(), row.spec(), row.length());
    }

    private MaterialImportData toImportData(MaterialImportDTO row, int rowNumber) {
        return new MaterialImportData(
                row.materialCode(),
                row.brand(),
                row.material(),
                row.category(),
                row.spec(),
                row.length(),
                row.unit(),
                row.quantityUnit(),
                parseBigDecimalOrZero(row.pieceWeightTon(), rowNumber, "件重(吨)"),
                parseIntegerOrZero(row.piecesPerBundle(), rowNumber, "每件支数"),
                parseBigDecimalOrZero(row.unitPrice(), rowNumber, "单价"),
                row.remark()
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
