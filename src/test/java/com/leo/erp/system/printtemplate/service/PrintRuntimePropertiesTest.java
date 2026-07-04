package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrintRuntimePropertiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintRuntimeProperties properties = new PrintRuntimeProperties(objectMapper);

    @Test
    void shouldReadSourceAndPrintableModulesFromRuntimeConfig() {
        PrintRecordSource source = properties.source("receipt");

        assertThat(properties.printableModules()).contains("purchase-order", "receipt");
        assertThat(source.tableName()).isEqualTo("fm_receipt");
        assertThat(source.itemTableName()).isEqualTo("fm_receipt_allocation");
        assertThat(source.itemFkColumn()).isEqualTo("receipt_id");
        assertThat(source.productPrintItems()).isFalse();
        assertThat(source.printItemAmount()).isFalse();
        assertThat(source.settlementModeColumn()).isEmpty();
        assertThat(source.allocationAmountColumn()).isEqualTo("allocated_amount");
    }

    @Test
    void shouldResolveDefaultPdfLayoutsByPrefixExactSuffixAndDefaultRule() {
        assertThat(properties.defaultPdfFormLayout("purchase-order"))
                .isEqualTo("print-forms/default-purchase.layout.json");
        assertThat(properties.defaultPdfFormLayout("sales-outbound"))
                .isEqualTo("print-forms/default-sales.layout.json");
        assertThat(properties.defaultPdfFormLayout("freight-bill"))
                .isEqualTo("print-forms/default-logistics.layout.json");
        assertThat(properties.defaultPdfFormLayout("customer-statement"))
                .isEqualTo("print-forms/default-statement.layout.json");
        assertThat(properties.defaultPdfFormLayout("unknown"))
                .isEqualTo("print-forms/yingjie-a4-remark.layout.json");
    }

    @Test
    void shouldReadFallbackAndScalarValues() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "intScale": 4,
                  "weightScale": "weight",
                  "priceScale": "price",
                  "unknownScale": "other",
                  "blankName": " ",
                  "name": "客户名称",
                  "count": 7,
                  "enabled": true,
                  "nullable": null
                }
                """);

        assertThat(properties.templateDefault("remark")).isEqualTo("无");
        assertThat(properties.templateDefault("missing")).isEmpty();
        assertThat(properties.scale(node, "intScale", 9)).isEqualTo(4);
        assertThat(properties.scale(node, "weightScale", 9)).isEqualTo(PrintRecordFieldFormatter.WEIGHT_SCALE);
        assertThat(properties.scale(node, "priceScale", 9)).isEqualTo(PrintRecordFieldFormatter.PRICE_SCALE);
        assertThat(properties.scale(node, "unknownScale", 9)).isEqualTo(9);
        assertThat(properties.scale(node, "missing", 9)).isEqualTo(9);
        assertThat(properties.formatName(node, "blankName", "fallback")).isEqualTo("fallback");
        assertThat(properties.formatName(node, "name", "fallback")).isEqualTo("客户名称");
        assertThat(properties.text(node, "nullable", "fallback")).isEqualTo("fallback");
        assertThat(properties.integer(node, "count", 0)).isEqualTo(7);
        assertThat(properties.integer(node, "missing", 3)).isEqualTo(3);
        assertThat(properties.bool(node, "enabled", false)).isTrue();
        assertThat(properties.bool(node, "missing", true)).isTrue();
        assertThat(properties.isQuantityFormat("quantity")).isTrue();
        assertThat(properties.isQuantityFormat("weight")).isFalse();
    }

    @Test
    void shouldReadChildCollections() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "array": [
                    {"name": "a"},
                    "ignored",
                    {"name": "b"}
                  ],
                  "texts": ["A", "", " ", "B"],
                  "object": {"a": 1, "b": 2}
                }
                """);

        assertThat(properties.childObjects(node.path("array"))).hasSize(2);
        assertThat(properties.childObjects(node.path("missing"))).isEmpty();
        assertThat(properties.childTextValues(node.path("texts"))).containsExactly("A", "B");
        assertThat(properties.childTextValues(node.path("missing"))).isEmpty();
        assertThat(properties.childFields(node.path("object")).keySet()).containsExactly("a", "b");
        assertThat(properties.childFields(node.path("texts"))).isEqualTo(Map.of());
    }

    @Test
    void shouldRejectUnsupportedModuleAndInvalidIdentifiers() throws Exception {
        assertThatThrownBy(() -> properties.source("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的打印模块");

        PrintRuntimeProperties invalid = propertiesFrom("""
                {
                  "modules": {
                    "bad": {
                      "tableName": "1bad",
                      "itemTableName": "item_table",
                      "itemFkColumn": "item_id"
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> invalid.source("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法标识符");
    }

    @Test
    void shouldRejectDefaultPdfLayoutConfigWithoutResource() throws Exception {
        PrintRuntimeProperties invalid = propertiesFrom("""
                {
                  "defaultPdfFormLayouts": [
                    {"default": true}
                  ]
                }
                """);

        assertThatThrownBy(() -> invalid.defaultPdfFormLayout("anything"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少字段: resource");
    }

    @Test
    void shouldSkipDefaultPdfLayoutRuleWithoutAnyMatcher() throws Exception {
        PrintRuntimeProperties custom = propertiesFrom("""
                {
                  "defaultPdfFormLayouts": [
                    {"resource": "unused.json"},
                    {"default": true, "resource": "default.json"}
                  ]
                }
                """);

        assertThat(custom.defaultPdfFormLayout("anything")).isEqualTo("default.json");
    }

    @Test
    void shouldRejectWhenNoDefaultPdfLayoutRuleMatches() throws Exception {
        PrintRuntimeProperties invalid = propertiesFrom("""
                {
                  "defaultPdfFormLayouts": [
                    {"billTypePrefix": "purchase-", "resource": "purchase.json"}
                  ]
                }
                """);

        assertThatThrownBy(() -> invalid.defaultPdfFormLayout("sales-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少默认 PDF_FORM 布局配置");
    }

    @Test
    void shouldRejectNonObjectRuntimeRoot() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readTree(anyString())).thenReturn(NullNode.getInstance());

        assertThatThrownBy(() -> new PrintRuntimeProperties(mapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON 对象");
    }

    @Test
    void shouldRejectNullRuntimeRoot() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readTree(anyString())).thenReturn(null);

        assertThatThrownBy(() -> new PrintRuntimeProperties(mapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON 对象");
    }

    @Test
    void shouldWrapRuntimeConfigReadFailure() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readTree(anyString())).thenThrow(JsonMappingException.from((JsonParser) null, "boom"));

        assertThatThrownBy(() -> new PrintRuntimeProperties(mapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取打印运行时配置失败");
    }

    private PrintRuntimeProperties propertiesFrom(String json) throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readTree(anyString())).thenReturn(objectMapper.readTree(json));
        return new PrintRuntimeProperties(mapper);
    }
}
