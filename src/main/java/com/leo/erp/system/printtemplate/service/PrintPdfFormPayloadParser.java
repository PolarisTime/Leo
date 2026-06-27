package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
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

    private Map<String, String> data(Object rawData) {
        if (!(rawData instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Map<String, String>> items(Object rawItems) {
        if (!(rawItems instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(this::data)
                .toList();
    }
}
