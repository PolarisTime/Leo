package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintPdfFormTemplateValidatorTest {

    private final PrintPdfFormTemplateValidator validator = new PrintPdfFormTemplateValidator(new ObjectMapper());

    @Test
    void shouldAcceptTemplateWithStaticFieldsOrTableLayout() {
        assertThat(validator.validate("{\"static\":[]}").path("static").isArray()).isTrue();
        assertThat(validator.validate("{\"fields\":{}}").path("fields").isObject()).isTrue();
        assertThat(validator.validate("{\"table\":{}}").path("table").isObject()).isTrue();
        assertThat(validator.validate(validTablesJson()).path("tables").isArray()).isTrue();
        assertThat(validator.validate("""
                {
                  "table": [],
                  "tables": [
                    {
                      "key": "items",
                      "source": "items",
                      "columns": [{"key": "material"}]
                    }
                  ]
                }
                """).path("tables").isArray()).isTrue();
        assertThat(validator.validate("""
                {
                  "tables": [
                    {
                      "key": "charges",
                      "source": "chargeItems",
                      "columns": [{"key": "chargeName", "width": 120}]
                    },
                    {
                      "key": "custom",
                      "source": "customSection",
                      "columns": [{"key": "customName"}]
                    }
                  ]
                }
                """).path("tables")).hasSize(2);
    }

    @Test
    void shouldRejectInvalidJsonAndNonObjectTemplate() {
        assertThatThrownBy(() -> validator.validate("{invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
        assertThatThrownBy(() -> validator.validate("[]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON 对象");
        assertThatThrownBy(() -> validator.validate("null"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON 对象");
    }

    @Test
    void shouldRejectWhenJsonReaderReturnsNullConfig() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ObjectReader reader = mock(ObjectReader.class);
        when(objectMapper.reader()).thenReturn(reader);
        when(reader.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).thenReturn(reader);
        when(reader.forType(com.fasterxml.jackson.databind.JsonNode.class)).thenReturn(reader);
        when(reader.readValue(anyString())).thenReturn(null);
        PrintPdfFormTemplateValidator validator = new PrintPdfFormTemplateValidator(objectMapper);

        assertThatThrownBy(() -> validator.validate("{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON 对象");
    }

    @Test
    void shouldRejectFormConfigAndEmptyLayout() {
        assertThatThrownBy(() -> validator.validate("{\"form\":{}}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 form 专用配置");
        assertThatThrownBy(() -> validator.validate("{\"static\":{},\"fields\":[],\"table\":[]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须配置通用字段或明细布局");
        assertThatThrownBy(() -> validator.validate("{\"tables\":[]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须配置通用字段或明细布局");
    }

    @Test
    void shouldRejectTablesWithMissingRequiredFieldsOrDuplicateKeys() {
        assertInvalidTable("""
                {"tables":[{"source":"items","columns":[{"key":"material"}]}]}
                """, "key");
        assertInvalidTable("""
                {"tables":[{"key":"items","columns":[{"key":"material"}]}]}
                """, "source");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items"}]}
                """, "columns");
        assertInvalidTable("""
                {
                  "tables": [
                    {"key": "items", "source": "items", "columns": [{"key": "material"}]},
                    {"key": "items", "source": "chargeItems", "columns": [{"key": "chargeName"}]}
                  ]
                }
                """, "key 不能重复");
    }

    @Test
    void shouldRejectTablesWithInvalidSource() {
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"","columns":[{"key":"material"}]}]}
                """, "source");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":1,"columns":[{"key":"material"}]}]}
                """, "source");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"${items}","columns":[{"key":"material"}]}]}
                """, "source");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items[0]","columns":[{"key":"material"}]}]}
                """, "source");
    }

    @Test
    void shouldRejectTablesWithInvalidColumns() {
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items","columns":[]}]}
                """, "columns");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items","columns":[{}]}]}
                """, "columns[].key");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items","columns":[{"key":""}]}]}
                """, "columns[].key");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items","columns":[{"key":"material","width":0}]}]}
                """, "width");
        assertInvalidTable("""
                {"tables":[{"key":"items","source":"items","columns":[{"key":"material","width":-1}]}]}
                """, "width");
    }

    private void assertInvalidTable(String templateHtml, String message) {
        assertThatThrownBy(() -> validator.validate(templateHtml))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message);
    }

    private String validTablesJson() {
        return """
                {
                  "tables": [
                    {
                      "key": "items",
                      "source": "items",
                      "columns": [
                        {"key": "material", "width": 80}
                      ]
                    }
                  ]
                }
                """;
    }
}
