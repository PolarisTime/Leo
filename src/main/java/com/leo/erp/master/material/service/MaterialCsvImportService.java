package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MaterialCsvImportService {

    private final MaterialCsvFileReader fileReader;
    private final MaterialCsvRowMapper rowMapper;
    private final MaterialImportProcessor importProcessor;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;

    public MaterialCsvImportService(MaterialCsvFileReader fileReader,
                                    MaterialCsvRowMapper rowMapper,
                                    MaterialImportProcessor importProcessor,
                                    TradeItemMaterialSupport tradeItemMaterialSupport) {
        this.fileReader = fileReader;
        this.rowMapper = rowMapper;
        this.importProcessor = importProcessor;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
    }

    @Transactional
    public MaterialCsvImportResult importBytes(byte[] raw) throws IOException {
        MaterialCsvFileReader.CsvTable table = fileReader.read(raw);
        MaterialImportProcessor.ImportSession session = importProcessor.start(collectIdentities(table));
        ImportCounters counters = new ImportCounters();
        List<MaterialCsvImportResult.Failure> failures = new ArrayList<>();
        List<MaterialCsvImportResult.RowTrace> rows = new ArrayList<>();

        for (int index = 1; index < table.rows().size(); index++) {
            List<String> row = table.rows().get(index);
            if (rowMapper.isBlank(row)) {
                continue;
            }
            counters.incrementTotal();
            importRow(session, table.headerIndexes(), row, index + 1, counters, failures, rows);
        }

        if (counters.successCount() > 0) {
            tradeItemMaterialSupport.evictCache();
        }
        return counters.toResult(failures, rows);
    }

    private void importRow(MaterialImportProcessor.ImportSession session,
                           Map<String, Integer> headerIndexes,
                           List<String> row,
                           int rowNumber,
                           ImportCounters counters,
                           List<MaterialCsvImportResult.Failure> failures,
                           List<MaterialCsvImportResult.RowTrace> rows) {
        String materialCode = rowMapper.materialCode(row, headerIndexes);
        try {
            MaterialImportData data = rowMapper.toImportData(row, headerIndexes, rowNumber);
            MaterialImportProcessor.ImportRowResult result = importProcessor.importRow(session, data, rowNumber);
            counters.increment(result.outcome());
            rows.add(new MaterialCsvImportResult.RowTrace(
                    rowNumber, safe(data.materialCode()), data.brand(), data.material(),
                    data.spec(), data.length(), result.outcome().name(), null));
        } catch (BusinessException exception) {
            failures.add(failure(rowNumber, materialCode, exception.getMessage()));
            rows.add(MaterialCsvImportResult.RowTrace.failed(rowNumber, materialCode, exception.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            failures.add(failure(rowNumber, materialCode, "保存失败，请检查该行数据"));
            rows.add(MaterialCsvImportResult.RowTrace.failed(rowNumber, materialCode, "保存失败，请检查该行数据"));
        } catch (DataAccessException exception) {
            failures.add(failure(rowNumber, materialCode, "保存失败，请检查该行数据"));
            rows.add(MaterialCsvImportResult.RowTrace.failed(rowNumber, materialCode, "保存失败，请检查该行数据"));
        }
    }

    private List<MaterialIdentityService.Identity> collectIdentities(MaterialCsvFileReader.CsvTable table) {
        List<MaterialIdentityService.Identity> identities = new ArrayList<>();
        for (int index = 1; index < table.rows().size(); index++) {
            List<String> row = table.rows().get(index);
            if (rowMapper.isBlank(row)) {
                continue;
            }
            try {
                identities.add(rowMapper.toIdentity(row, table.headerIndexes(), index + 1));
            } catch (BusinessException ignored) {
                // 无效行由正式导入循环生成行级失败记录。
            }
        }
        return identities;
    }

    private MaterialCsvImportResult.Failure failure(int rowNumber, String materialCode, String reason) {
        return new MaterialCsvImportResult.Failure(rowNumber, safe(materialCode), reason);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ImportCounters {

        private int totalRows;
        private int createdCount;
        private int updatedCount;
        private int skippedCount;

        void incrementTotal() {
            totalRows++;
        }

        void increment(MaterialImportProcessor.ImportOutcome outcome) {
            switch (outcome) {
                case CREATED -> createdCount++;
                case UPDATED -> updatedCount++;
                case SKIPPED -> skippedCount++;
            }
        }

        int successCount() {
            return createdCount + updatedCount;
        }

        MaterialCsvImportResult toResult(List<MaterialCsvImportResult.Failure> failures,
                                         List<MaterialCsvImportResult.RowTrace> rows) {
            return new MaterialCsvImportResult(
                    totalRows,
                    successCount(),
                    createdCount,
                    updatedCount,
                    skippedCount,
                    failures.size(),
                    List.copyOf(failures),
                    List.copyOf(rows)
            );
        }
    }
}
