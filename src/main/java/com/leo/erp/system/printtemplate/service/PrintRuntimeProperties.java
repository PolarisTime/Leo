package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PrintRuntimeProperties {

    private static final String CONFIG_RESOURCE = "print-runtime.json";
    private static final String WEIGHT_SCALE_KEY = "weight";
    private static final String PRICE_SCALE_KEY = "price";
    private static final String QUANTITY_FORMAT = "quantity";
    private static final String DEFAULT_TEMPLATE_VALUE = "";
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private final JsonNode root;

    public PrintRuntimeProperties(ObjectMapper objectMapper) {
        this.root = readRoot(objectMapper);
    }

    PrintRecordSource source(String moduleKey) {
        JsonNode module = module(moduleKey);
        return new PrintRecordSource(
                requiredIdentifier(module, "tableName"),
                requiredIdentifier(module, "itemTableName"),
                requiredIdentifier(module, "itemFkColumn"),
                bool(module, "productPrintItems", false),
                bool(module, "printItemAmount", false),
                optionalIdentifier(module, "settlementModeColumn"),
                optionalIdentifier(module, "allocationAmountColumn")
        );
    }

    List<String> printableModules() {
        List<String> result = new ArrayList<>();
        root.path("modules").fieldNames().forEachRemaining(result::add);
        return result;
    }

    JsonNode pieceWeightConfig() {
        return root.path("fieldFormatting").path("pieceWeightTon");
    }

    JsonNode topLevelFields() {
        return root.path("topLevelFields");
    }

    JsonNode totals() {
        return root.path("totals");
    }

    JsonNode legacyLayout() {
        return root.path("legacyLayout");
    }

    JsonNode enrichers(String moduleKey) {
        return root.path("enrichers").path(moduleKey);
    }

    JsonNode xlsxExport(String moduleKey) {
        return root.path("xlsxExports").path(moduleKey);
    }

    String defaultPdfFormLayout(String billType) {
        for (JsonNode rule : childObjects(root.path("defaultPdfFormLayouts"))) {
            if (matchesBillType(rule, billType)) {
                return requiredText(rule, "resource");
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "缺少默认 PDF_FORM 布局配置");
    }

    String templateDefault(String key) {
        return text(root.path("templateDefaults"), key, DEFAULT_TEMPLATE_VALUE);
    }

    int scale(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        if (value.isInt()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            return scale(value.asText(), fallback);
        }
        return fallback;
    }

    int scale(String key, int fallback) {
        if (WEIGHT_SCALE_KEY.equals(key)) {
            return PrintRecordFieldFormatter.WEIGHT_SCALE;
        }
        if (PRICE_SCALE_KEY.equals(key)) {
            return PrintRecordFieldFormatter.PRICE_SCALE;
        }
        return fallback;
    }

    String formatName(JsonNode node, String field, String fallback) {
        String value = text(node, field, fallback);
        return value.isBlank() ? fallback : value;
    }

    String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }

    int integer(JsonNode node, String field, int fallback) {
        JsonNode child = node.path(field);
        return child.isInt() ? child.asInt() : fallback;
    }

    boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : fallback;
    }

    List<JsonNode> childObjects(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isObject()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    List<String> childTextValues(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (!value.isBlank()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    Map<String, JsonNode> childFields(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, JsonNode> result = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private JsonNode module(String moduleKey) {
        JsonNode module = root.path("modules").path(moduleKey);
        if (!module.isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的打印模块: " + moduleKey);
        }
        return module;
    }

    private boolean matchesBillType(JsonNode rule, String billType) {
        if (bool(rule, "default", false)) {
            return true;
        }
        String exact = text(rule, "billType", "");
        if (!exact.isBlank()) {
            return exact.equals(billType);
        }
        String prefix = text(rule, "billTypePrefix", "");
        if (!prefix.isBlank()) {
            return billType.startsWith(prefix);
        }
        String suffix = text(rule, "billTypeSuffix", "");
        return !suffix.isBlank() && billType.endsWith(suffix);
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "打印运行时配置缺少字段: " + field);
        }
        return value;
    }

    private String requiredIdentifier(JsonNode node, String field) {
        return validateIdentifier(requiredText(node, field), field);
    }

    private String optionalIdentifier(JsonNode node, String field) {
        String value = text(node, field, "");
        return value.isBlank() ? "" : validateIdentifier(value, field);
    }

    private String validateIdentifier(String value, String field) {
        if (!SQL_IDENTIFIER.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "打印运行时配置字段不是合法标识符: " + field);
        }
        return value;
    }

    private JsonNode readRoot(ObjectMapper objectMapper) {
        try {
            String content = new ClassPathResource(CONFIG_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(content);
            if (node == null || !node.isObject()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "打印运行时配置不是合法 JSON 对象");
            }
            return node;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取打印运行时配置失败");
        }
    }

    boolean isQuantityFormat(String format) {
        return QUANTITY_FORMAT.equals(format);
    }
}
