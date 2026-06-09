package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PrintPdfFormTemplateValidator {

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
        if (!config.path("static").isArray() && !config.path("fields").isObject() && !config.path("table").isObject()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF_FORM 模板必须配置通用字段或明细布局");
        }
        return config;
    }
}
