package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class PrintPdfFormPayloadParser {

    private final PrintPdfFormTemplateValidator templateValidator;

    public PrintPdfFormPayloadParser(PrintPdfFormTemplateValidator templateValidator) {
        this.templateValidator = templateValidator;
    }

    PrintPdfFormPayload parse(Map<String, Object> payload) {
        String templateType = String.valueOf(payload.getOrDefault("templateType", ""));
        if (!"PDF_FORM".equals(templateType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前模板不是 PDF_FORM 类型");
        }
        return new PrintPdfFormPayload(
                templateValidator.validate(String.valueOf(payload.getOrDefault("templateHtml", ""))),
                data(payload.getOrDefault("data", Collections.emptyMap())),
                items(payload.getOrDefault("items", Collections.emptyList()))
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> data(Object rawData) {
        return (Map<String, String>) rawData;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> items(Object rawItems) {
        return (List<Map<String, String>>) rawItems;
    }
}
