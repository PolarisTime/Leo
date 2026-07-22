package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrintXlsxExportLayoutProvider {

    private final PrintRuntimeProperties runtimeProperties;

    public PrintXlsxExportLayoutProvider(PrintRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    public PrintXlsxExportLayout layout(String moduleKey) {
        JsonNode config = runtimeProperties.xlsxExport(moduleKey);
        if (!config.isObject()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "缺少 Excel 打印导出配置: " + moduleKey);
        }
        JsonNode detail = config.path("detail");
        JsonNode summary = config.path("summary");
        return new PrintXlsxExportLayout(
                moduleKey,
                requiredText(config, "templateResource"),
                runtimeProperties.text(config, "sheetName", ""),
                Math.max(1, runtimeProperties.integer(config, "rowsPerPage", 1)),
                runtimeProperties.integer(detail, "startRow", 0),
                runtimeProperties.integer(detail, "endColumn", 0),
                headerCells(config.path("header")),
                detailColumns(detail.path("columns")),
                summary(summary),
                pieceWeight(config.path("pieceWeight"))
        );
    }

    private List<PrintXlsxExportLayout.HeaderCell> headerCells(JsonNode node) {
        return runtimeProperties.childObjects(node).stream()
                .map(cell -> new PrintXlsxExportLayout.HeaderCell(
                        runtimeProperties.text(cell, "field", ""),
                        requiredText(cell, "cell")
                ))
                .toList();
    }

    private List<PrintXlsxExportLayout.DetailColumn> detailColumns(JsonNode node) {
        return runtimeProperties.childObjects(node).stream()
                .map(column -> new PrintXlsxExportLayout.DetailColumn(
                        runtimeProperties.text(column, "field", ""),
                        runtimeProperties.integer(column, "column", 0),
                        runtimeProperties.text(column, "type", "text")
                ))
                .toList();
    }

    private PrintXlsxExportLayout.Summary summary(JsonNode node) {
        return new PrintXlsxExportLayout.Summary(
                runtimeProperties.integer(node, "row", 0),
                runtimeProperties.childObjects(node.path("cells")).stream()
                        .map(this::summaryCell)
                        .toList()
        );
    }

    private PrintXlsxExportLayout.SummaryCell summaryCell(JsonNode node) {
        return new PrintXlsxExportLayout.SummaryCell(
                runtimeProperties.text(node, "field", ""),
                runtimeProperties.integer(node, "column", 0),
                runtimeProperties.text(node, "type", "text"),
                runtimeProperties.text(node, "text", ""),
                runtimeProperties.scale(node, "scale", PrintRecordFieldFormatter.PRICE_SCALE),
                runtimeProperties.text(node, "suffix", "")
        );
    }

    private PrintXlsxExportLayout.PieceWeight pieceWeight(JsonNode node) {
        JsonNode fallback = runtimeProperties.pieceWeightConfig();
        JsonNode config = node.isObject() ? node : fallback;
        JsonNode suppressWhen = config.has("suppressWhen") ? config.path("suppressWhen") : fallback.path("suppressWhen");
        return new PrintXlsxExportLayout.PieceWeight(
                runtimeProperties.text(config, "replacement", runtimeProperties.text(fallback, "replacement", "-")),
                runtimeProperties.scale(config, "scale", PrintRecordFieldFormatter.WEIGHT_SCALE),
                runtimeProperties.childObjects(suppressWhen).stream()
                        .map(rule -> new PrintXlsxExportLayout.SuppressRule(
                                runtimeProperties.text(rule, "field", ""),
                                runtimeProperties.childTextValues(rule.path("values"))
                        ))
                        .toList()
        );
    }

    private String requiredText(JsonNode node, String field) {
        String value = runtimeProperties.text(node, field, "");
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Excel 打印导出配置缺少字段: " + field);
        }
        return value;
    }
}
