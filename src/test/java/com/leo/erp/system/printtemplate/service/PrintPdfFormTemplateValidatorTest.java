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
    }
}
