package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PrintPdfFormTemplateValidator {

    private static final Pattern SOURCE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final ObjectReader jsonReader;

    public PrintPdfFormTemplateValidator(ObjectMapper objectMapper) {
        this.jsonReader = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .forType(JsonNode.class);
    }

    public JsonNode validate(String templateHtml) {
        JsonNode config;
        try {
            config = jsonReader.readValue(templateHtml);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 表单模板配置不是合法 JSON");
        }
        if (config == null || !config.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 表单模板配置不是合法 JSON 对象");
        }
        if (config.has("form")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF_FORM 模板不支持 form 专用配置，请使用通用布局 JSON");
        }
        boolean hasTables = config.has("tables");
        if (hasTables) {
            validateTables(config.path("tables"));
        }
        if (!config.path("static").isArray()
                && !config.path("fields").isObject()
                && !config.path("table").isObject()
                && !hasTables) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF_FORM 模板必须配置通用字段或明细布局");
        }
        return config;
    }

    private void validateTables(JsonNode tables) {
        if (!tables.isArray() || tables.isEmpty()) {
            throw validationError("PDF_FORM 模板必须配置通用字段或明细布局，tables[] 不能为空");
        }
        Set<String> tableKeys = new HashSet<>();
        for (JsonNode table : tables) {
            validateTable(table, tableKeys);
        }
    }

    private void validateTable(JsonNode table, Set<String> tableKeys) {
        if (!table.isObject()) {
            throw validationError("PDF_FORM 模板 tables[] 每个表必须是对象");
        }
        String key = requireNonBlankText(table, "key", "PDF_FORM 模板 tables[].key 必须是非空字符串");
        if (!tableKeys.add(key)) {
            throw validationError("PDF_FORM 模板 tables[].key 不能重复");
        }
        validateSource(table.path("source"));
        validateColumns(table.path("columns"));
    }

    private void validateSource(JsonNode source) {
        if (!source.isTextual() || source.asText().isBlank() || !SOURCE_IDENTIFIER.matcher(source.asText()).matches()) {
            throw validationError("PDF_FORM 模板 tables[].source 必须是合法标识符");
        }
    }

    private void validateColumns(JsonNode columns) {
        if (!columns.isArray() || columns.isEmpty()) {
            throw validationError("PDF_FORM 模板 tables[].columns 不能为空");
        }
        for (JsonNode column : columns) {
            validateColumn(column);
        }
    }

    private void validateColumn(JsonNode column) {
        if (!column.isObject()) {
            throw validationError("PDF_FORM 模板 tables[].columns[] 必须是对象");
        }
        requireNonBlankText(column, "key", "PDF_FORM 模板 tables[].columns[].key 必须是非空字符串");
        JsonNode width = column.path("width");
        if (!width.isMissingNode() && (!width.isNumber() || width.asDouble() <= 0)) {
            throw validationError("PDF_FORM 模板 tables[].columns[].width 必须大于 0");
        }
    }

    private String requireNonBlankText(JsonNode node, String field, String message) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw validationError(message);
        }
        return value.asText();
    }

    private BusinessException validationError(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
